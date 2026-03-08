"""
example_crawler.py - 示例爬虫模板
针对某个无 API 的法律网站编写，继承 BaseCrawler 只需实现 run() 方法。

使用方式：
  1. 复制此文件，重命名为 {网站名}_crawler.py
  2. 修改 SOURCE_NAME、BASE_URL 和 run() 中的爬取逻辑
  3. 通过接口触发：POST /api/admin/crawler/python/start?crawler={网站名}
"""

import time
import requests
from bs4 import BeautifulSoup
from base_crawler import BaseCrawler

SOURCE_NAME = "example_site"
BASE_URL = "https://example-law-site.com"

# 涉华过滤关键词
CHINA_INDICATORS = [
    "china", "chinese", "prc", "people's republic",
    "huawei", "zte", "alibaba", "bytedance", "tiktok",
    "beijing", "shanghai", "shenzhen",
    "chinese national", "chinese citizen", "chinese company",
]


def is_china_related(text: str, threshold: int = 2) -> bool:
    """判断文本是否涉华（命中关键词数 >= threshold）"""
    if not text:
        return False
    lower = text.lower()
    hits = sum(1 for kw in CHINA_INDICATORS if kw in lower)
    return hits >= threshold


class ExampleCrawler(BaseCrawler):

    def run(self):
        """实现具体爬取逻辑"""
        print(f"[{SOURCE_NAME}] 开始爬取...")

        # ── 示例：分页爬取列表页 ──────────────────────────────
        for page in range(1, 11):  # 最多爬10页
            list_url = f"{BASE_URL}/cases?page={page}"
            print(f"[{SOURCE_NAME}] 爬取第 {page} 页: {list_url}")

            try:
                resp = requests.get(list_url, timeout=15, headers={
                    "User-Agent": "Mozilla/5.0 (compatible; LawCaseBot/1.0)"
                })
                resp.raise_for_status()
            except Exception as e:
                print(f"[错误] 列表页请求失败: {e}")
                break

            soup = BeautifulSoup(resp.text, "html.parser")

            # ── 根据实际页面结构修改选择器 ──────────────────────
            case_links = soup.select("a.case-link")  # 示例选择器，需按实际修改
            if not case_links:
                print(f"[{SOURCE_NAME}] 第 {page} 页无结果，停止")
                break

            for link in case_links:
                case_url = BASE_URL + link.get("href", "")
                case_name = link.get_text(strip=True)

                # 一次过滤：标题涉华（宽松）
                if not is_china_related(case_name, threshold=1):
                    continue

                # 获取详情页
                detail = self._fetch_detail(case_url)
                if not detail:
                    continue

                # 二次过滤：正文涉华（严格）
                if not is_china_related(detail.get("content_en", ""), threshold=2):
                    continue

                self.save({
                    "title_en": case_name,
                    "content_en": detail.get("content_en"),
                    "court": detail.get("court"),
                    "judgment_date": detail.get("judgment_date"),
                    "country": "USA",
                    "source": SOURCE_NAME,
                    "url": case_url,
                })

                time.sleep(1)  # 礼貌延迟

            time.sleep(2)

        print(f"[{SOURCE_NAME}] 爬取完成")

    def _fetch_detail(self, url: str) -> dict | None:
        """获取案例详情页内容，根据实际页面结构修改"""
        try:
            resp = requests.get(url, timeout=15, headers={
                "User-Agent": "Mozilla/5.0 (compatible; LawCaseBot/1.0)"
            })
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "html.parser")

            # ── 根据实际页面结构修改以下选择器 ──────────────────
            content = soup.select_one(".case-content")
            court = soup.select_one(".court-name")
            date = soup.select_one(".judgment-date")

            return {
                "content_en": content.get_text(separator="\n", strip=True) if content else "",
                "court": court.get_text(strip=True) if court else "",
                "judgment_date": date.get_text(strip=True) if date else None,
            }
        except Exception as e:
            print(f"[错误] 详情页获取失败 {url}: {e}")
            return None


if __name__ == "__main__":
    ExampleCrawler().start()
