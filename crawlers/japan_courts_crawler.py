"""
japan_courts_crawler.py - 日本裁判所判例検索爬虫

来源网站: https://www.courts.go.jp/hanrei/search1/index.html
支持通过命令行参数控制所有检索条件，可由 Java 后端携带参数启动。

使用示例（直接运行）:
    python japan_courts_crawler.py --query1 中華 --max-pages 10

通过 Java 后端 API 触发:
    POST /api/admin/crawler/python/start-japan
    Body: { "query1": "中華", "maxPages": 10 }

关键说明:
    - 搜索 URL: https://www.courts.go.jp/hanrei/search1/index.html
    - 分页参数: offset（每页30条，offset=0,30,60,...）
    - 结果链接为相对路径，基于搜索页解析
    - 依赖系统代理（如 Clash/V2Ray）访问目标网站
"""

import re
import sys
import time
import argparse
from datetime import datetime
from typing import Optional

import mysql.connector
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin
from base_crawler import BaseCrawler

SOURCE_NAME = "japan_courts"
BASE_URL = "https://www.courts.go.jp"
SEARCH_URL = "https://www.courts.go.jp/hanrei/search1/index.html"
PAGE_SIZE = 30  # 每页固定30条

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "ja-JP,ja;q=0.9,zh-CN;q=0.8,zh;q=0.7",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "Referer": "https://www.courts.go.jp/hanrei/search1/index.html",
}

# 日本年号对照（起始西历年）
GENGO_MAP = {
    "明治": 1868,
    "大正": 1912,
    "昭和": 1926,
    "平成": 1989,
    "令和": 2019,
}


def _make_session() -> requests.Session:
    """创建 requests Session，走系统代理（Clash/V2Ray 等）。"""
    return requests.Session()


class JapanCourtsCrawler(BaseCrawler):

    def __init__(self, args, db_config: dict = None):
        super().__init__(db_config)
        self.args = args
        self.session = _make_session()

    # ─── crawl_job_record 回写（与 Java 端 status 一致：1=成功 2=失败）────────────────

    def _finish_crawl_job_record(
        self, job_id: int, success: bool, saved_count: Optional[int], error: Optional[str]
    ):
        """在 self.conn 已连接时调用。"""
        cur = self.conn.cursor()
        now = datetime.now()
        if success:
            cur.execute(
                """
                UPDATE crawl_job_record
                SET status=%s, saved_count=%s, finished_at=%s, error_message=NULL
                WHERE id=%s
                """,
                (1, saved_count, now, job_id),
            )
        else:
            err = (error or "")[:1000] if error else None
            cur.execute(
                """
                UPDATE crawl_job_record
                SET status=%s, saved_count=NULL, finished_at=%s, error_message=%s
                WHERE id=%s
                """,
                (2, now, err, job_id),
            )
        self.conn.commit()
        cur.close()
        print(
            f"[{SOURCE_NAME}] 已更新 crawl_job_record id={job_id} "
            f"status={'成功' if success else '失败'} saved_count={saved_count}"
        )

    def _try_finish_crawl_job_failed(self, job_id: int, error: str):
        """异常时写回失败；优先用当前连接，否则临时连接数据库。"""
        err = (error or "")[:1000]
        now = datetime.now()
        try:
            if self.conn and self.conn.is_connected():
                cur = self.conn.cursor()
                cur.execute(
                    """
                    UPDATE crawl_job_record
                    SET status=2, saved_count=NULL, finished_at=%s, error_message=%s
                    WHERE id=%s
                    """,
                    (now, err, job_id),
                )
                self.conn.commit()
                cur.close()
                print(f"[{SOURCE_NAME}] 已更新 crawl_job_record id={job_id} 为失败")
                return
        except Exception as ex:
            print(f"[警告] 使用当前连接写回 crawl_job_record 失败: {ex}")
        try:
            c = mysql.connector.connect(**self.db_config)
            cur = c.cursor()
            cur.execute(
                """
                UPDATE crawl_job_record
                SET status=2, saved_count=NULL, finished_at=%s, error_message=%s
                WHERE id=%s
                """,
                (now, err, job_id),
            )
            c.commit()
            cur.close()
            c.close()
            print(f"[{SOURCE_NAME}] 已更新 crawl_job_record id={job_id} 为失败（临时连接）")
        except Exception as ex:
            print(f"[警告] 无法写回 crawl_job_record: {ex}")

    # ─── 主流程 ───────────────────────────────────────────────────────────────

    def run(self):
        job_id = getattr(self.args, "crawl_job_id", None)
        query1 = self.args.query1 or ""
        query2 = self.args.query2 or ""
        max_pages = max(1, self.args.max_pages or 50)

        print(f"[{SOURCE_NAME}] 开始爬取 | query1={query1!r} query2={query2!r} max_pages={max_pages}")

        total_saved = 0
        page = 1

        try:
            while page <= max_pages:
                offset = (page - 1) * PAGE_SIZE
                print(f"[{SOURCE_NAME}] ── 第 {page} 页（offset={offset}）──")

                cases = self._fetch_list_page(query1, query2, offset)

                if cases is None:
                    print(f"[{SOURCE_NAME}] 列表页请求失败，停止")
                    break
                if not cases:
                    print(f"[{SOURCE_NAME}] 无更多结果，停止")
                    break

                page_saved = 0
                for case_basic in cases:
                    # 尝试进入详情页获取全文
                    detail_content = ""
                    detail_url = case_basic.get("url", "")
                    if detail_url:
                        detail_content = self._fetch_detail_content(detail_url)
                        time.sleep(1.0)

                    saved = self.save({
                        "case_no":       case_basic.get("case_no"),
                        "title_en":      case_basic.get("case_name"),
                        "country":       "日本",
                        "court":         case_basic.get("court"),
                        "judgment_date": case_basic.get("judgment_date"),
                        "content_en":    detail_content or case_basic.get("summary", ""),
                        "keywords":      ",".join(filter(None, [query1, query2])),
                        "source":        SOURCE_NAME,
                        "url":           detail_url,
                        "pdf_url":       case_basic.get("pdf_url", ""),
                    })
                    if saved:
                        page_saved += 1
                        total_saved += 1

                print(f"[{SOURCE_NAME}] 第 {page} 页完成，本页入库 {page_saved} 条，累计 {total_saved} 条")

                # 如果本页结果不足一页，说明已到最后一页
                if len(cases) < PAGE_SIZE:
                    print(f"[{SOURCE_NAME}] 已到达最后一页，停止")
                    break

                page += 1
                time.sleep(2)

            print(f"[{SOURCE_NAME}] 全部完成，共入库 {total_saved} 条")
            if job_id is not None:
                self._finish_crawl_job_record(int(job_id), True, total_saved, None)
        except Exception as e:
            print(f"[{SOURCE_NAME}] 爬取异常: {e}")
            if job_id is not None:
                self._try_finish_crawl_job_failed(int(job_id), str(e))
            raise

    # ─── 列表页爬取 ───────────────────────────────────────────────────────────

    def _fetch_list_page(self, query1: str, query2: str, offset: int):
        """
        请求搜索列表页，直接从列表提取案例基本信息。
        返回 list[dict] 或 None（请求失败）。
        """
        params = {
            "query1": query1,
            "query2": query2,
            "sort": "1",          # 裁判年月日降順
            "offset": str(offset),
            "filter[judgeGengoFrom]":  self.args.judge_gengo_from or "",
            "filter[judgeYearFrom]":   self.args.judge_year_from  or "",
            "filter[judgeMonthFrom]":  self.args.judge_month_from or "",
            "filter[judgeDayFrom]":    self.args.judge_day_from   or "",
            "filter[judgeGengoTo]":    self.args.judge_gengo_to   or "",
            "filter[judgeYearTo]":     self.args.judge_year_to    or "",
            "filter[judgeMonthTo]":    self.args.judge_month_to   or "",
            "filter[judgeDayTo]":      self.args.judge_day_to     or "",
            "filter[jikenGengo]":      "",
            "filter[jikenYear]":       "",
            "filter[jikenCode]":       "",
            "filter[jikenNumber]":     "",
            "filter[courtType]":       self.args.court_type  or "",
            "filter[courtSection]":    "",
            "filter[courtName]":       self.args.court_name  or "",
            "filter[branchName]":      self.args.branch_name or "",
        }

        try:
            resp = self.session.get(SEARCH_URL, params=params, headers=HEADERS, timeout=30)
            resp.raise_for_status()
            resp.encoding = "utf-8"
        except Exception as e:
            print(f"[错误] 列表页请求失败 (offset={offset}): {e}")
            return None

        soup = BeautifulSoup(resp.text, "html.parser")
        return self._parse_list_rows(soup)

    def _parse_list_rows(self, soup: BeautifulSoup) -> list:
        """
        从搜索结果 HTML 的 <table class="search-result-table"> 中提取案例信息。

        表格结构：
          <tr>
            <th><a href="./../NNNNN/detailX/index.html">分类名</a></th>
            <td>
              <p>事件番号 事件名</p>
              <p>裁判年月日 裁判所名 支部名 裁判種別 結果 ...</p>
              <p>知財分类 ...</p>
              <p>争点 ...</p>
            </td>
            <td class="file-col">全文PDF链接</td>
          </tr>
        """
        results = []
        table = soup.find("table", class_="search-result-table")
        if not table:
            print("[警告] 未找到 search-result-table，页面可能结构有变化")
            return results

        for tr in table.find_all("tr"):
            th = tr.find("th")
            td = tr.find("td", class_=lambda c: not (c and "file-col" in c))
            if not th or not td:
                continue

            # 详情页 URL（th 中的 <a>）
            a_tag = th.find("a", href=True)
            if not a_tag:
                continue
            # 相对路径需要基于搜索页 URL 解析
            detail_url = urljoin(SEARCH_URL, a_tag["href"])

            # PDF 链接（file-col 列中的 <a href="...pdf">）
            pdf_url = ""
            file_td = tr.find("td", class_="file-col")
            if file_td:
                pdf_a = file_td.find("a", href=lambda h: h and ".pdf" in h.lower())
                if pdf_a:
                    pdf_url = urljoin(BASE_URL, pdf_a["href"])

            # td 中各段落（统一将换行/多余空格压缩为单个空格）
            paragraphs = [
                " ".join(p.get_text(" ", strip=True).split())
                for p in td.find_all("p")
            ]

            # 第1段：事件番号 + 事件名
            # 事件番号格式：令和7(わ)347 / 平成29(ワ)1844 / 令和7(行ケ)10032等
            case_no = ""
            case_name = ""
            if paragraphs:
                first = paragraphs[0]
                parts = first.split(None, 1)
                if len(parts) == 2 and re.search(r"\d", parts[0]):
                    case_no   = parts[0]
                    case_name = parts[1]
                else:
                    case_name = first

            # 第2段：裁判年月日 裁判所名 支部名 ...
            court = ""
            judgment_date = None
            if len(paragraphs) >= 2:
                second = paragraphs[1]
                # 提取日期：令和8年1月15日 / 平成29年11月19日
                judgment_date = _parse_japanese_date(second)
                # 提取裁判所名（日期后的第一个词）
                m2 = re.search(
                    r"(?:令和|平成|昭和|大正|明治)\d+年\d+月\d+日\s+(.+?)(?:\s|$)", second
                )
                if m2:
                    court = m2.group(1).strip()

            # 摘要（后续段落合并）
            summary = " ".join(paragraphs[1:]) if len(paragraphs) > 1 else ""

            results.append({
                "url":           detail_url,
                "case_no":       case_no,
                "case_name":     case_name,
                "court":         court,
                "judgment_date": judgment_date,
                "summary":       summary,
                "pdf_url":       pdf_url,
            })

        return results

    # ─── 详情页全文爬取 ───────────────────────────────────────────────────────

    def _fetch_detail_content(self, url: str) -> str:
        """
        请求详情页，提取判决全文内容。
        失败时返回空字符串。
        """
        try:
            resp = self.session.get(url, headers=HEADERS, timeout=30)
            resp.raise_for_status()
            resp.encoding = "utf-8"
        except Exception as e:
            print(f"[警告] 详情页请求失败 {url}: {e}")
            return ""

        soup = BeautifulSoup(resp.text, "html.parser")

        # 尝试常见正文容器选择器
        content_selectors = [
            ".hanreiContent", "#hanrei-body", ".case-content",
            ".hanreiBody", "#content-area",
            ".module-sub-page-parts-table",
            "article", "main",
        ]
        for sel in content_selectors:
            el = soup.select_one(sel)
            if el:
                text = el.get_text(separator="\n", strip=True)
                if len(text) > 50:
                    return text[:80000]

        # 降级：取 body 全文
        body = soup.find("body")
        if body:
            return body.get_text(separator="\n", strip=True)[:80000]

        return ""


# ─── 工具函数 ─────────────────────────────────────────────────────────────────

def _parse_japanese_date(date_str: str):
    """将日本年号/西历日期字符串转换为 YYYY-MM-DD 格式。"""
    if not date_str:
        return None

    # 西历：2023年3月15日
    m = re.search(r"(\d{4})年(\d{1,2})月(\d{1,2})日", date_str)
    if m:
        return f"{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"

    # 日本年号：令和5年3月15日、平成元年1月1日
    for gengo, start_year in GENGO_MAP.items():
        m = re.search(rf"{gengo}(\d+|元)年(\d{{1,2}})月(\d{{1,2}})日", date_str)
        if m:
            y_str = m.group(1)
            year  = 1 if y_str == "元" else int(y_str)
            ad_year = start_year + year - 1
            return f"{ad_year}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"

    return None


# ─── 命令行入口 ───────────────────────────────────────────────────────────────

def parse_args():
    parser = argparse.ArgumentParser(
        description="日本裁判所判例検索爬虫",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    # 检索参数
    search = parser.add_argument_group("检索条件")
    search.add_argument("--query1",            default="中華",  help="第1检索关键词")
    search.add_argument("--query2",            default="",      help="第2检索关键词（AND缩小范围）")
    search.add_argument("--judge-gengo-from",  default="",      help="判决日期起·元号（令和/平成/昭和）")
    search.add_argument("--judge-year-from",   default="",      help="判决日期起·年")
    search.add_argument("--judge-month-from",  default="",      help="判决日期起·月")
    search.add_argument("--judge-day-from",    default="",      help="判决日期起·日")
    search.add_argument("--judge-gengo-to",    default="",      help="判决日期止·元号")
    search.add_argument("--judge-year-to",     default="",      help="判决日期止·年")
    search.add_argument("--judge-month-to",    default="",      help="判决日期止·月")
    search.add_argument("--judge-day-to",      default="",      help="判决日期止·日")
    search.add_argument("--court-type",        default="",      help="裁判所种别（1最高/2高等/3地方/4家庭/5简易）")
    search.add_argument("--court-name",        default="",      help="裁判所名（如 東京地方裁判所）")
    search.add_argument("--branch-name",       default="",      help="支部名")
    search.add_argument("--max-pages",         default=50, type=int, help="最大爬取页数（每页30条）")
    search.add_argument(
        "--crawl-job-id",
        default=None,
        type=int,
        help="crawl_job_record 主键，结束时回写 status/saved_count/finished_at",
    )

    # 数据库参数
    db = parser.add_argument_group("数据库连接")
    db.add_argument("--db-host",     default="localhost",                  help="数据库主机")
    db.add_argument("--db-port",     default=3306, type=int,               help="数据库端口")
    db.add_argument("--db-user",     default="root",                       help="数据库用户名")
    db.add_argument("--db-password", default="123456",                     help="数据库密码")
    db.add_argument("--db-name",     default="foreign_law_case_system_db", help="数据库名")

    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    db_config = {
        "host":     args.db_host,
        "port":     args.db_port,
        "user":     args.db_user,
        "password": args.db_password,
        "database": args.db_name,
        "charset":  "utf8mb4",
    }
    JapanCourtsCrawler(args, db_config).start()
