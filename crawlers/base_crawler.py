"""
base_crawler.py
爬虫公共基类：提供数据库连接、去重判断、案例入库等公共逻辑。
所有子爬虫继承此类，只需实现 run() 方法。
"""

import mysql.connector
from datetime import datetime, date
from abc import ABC, abstractmethod


class BaseCrawler(ABC):

    def __init__(self, db_config: dict = None):
        """
        :param db_config: 数据库连接配置，不传则使用默认配置
        """
        self.db_config = db_config or {
            "host": "localhost",
            "port": 3306,
            "user": "root",
            "password": "123456",
            "database": "foreign_law_case_system_db",
            "charset": "utf8mb4",
        }
        self.conn = None

    def connect(self):
        self.conn = mysql.connector.connect(**self.db_config)
        print(f"[DB] 数据库连接成功")

    def close(self):
        if self.conn and self.conn.is_connected():
            self.conn.close()
            print(f"[DB] 数据库连接已关闭")

    def exists_by_url(self, url: str) -> bool:
        """根据 url 判断案例是否已存在（去重）"""
        cursor = self.conn.cursor()
        cursor.execute(
            "SELECT id FROM legal_case WHERE url = %s AND is_deleted = 0 LIMIT 1",
            (url,)
        )
        return cursor.fetchone() is not None

    def save(self, case: dict) -> bool:
        """
        将案例入库。
        :param case: 案例字典，字段与 legal_case 表对应
            必填：url, source
            可选：case_no, title_en, title_zh, case_reason, case_type,
                  country, court, judgment_date(date对象或"yyyy-MM-dd"字符串),
                  content_en, keywords
        :return: True=入库成功，False=已存在跳过
        """
        url = case.get("url", "")
        if not url:
            print("[跳过] url 为空")
            return False

        if self.exists_by_url(url):
            print(f"[跳过] 已存在: {url}")
            return False

        # 处理日期字段
        judgment_date = case.get("judgment_date")
        if isinstance(judgment_date, str) and judgment_date:
            try:
                judgment_date = datetime.strptime(judgment_date, "%Y-%m-%d").date()
            except ValueError:
                judgment_date = None

        now = datetime.now()
        title_en = case.get("title_en", "")

        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO legal_case
                (case_no, title_zh, title_en, case_reason, case_type,
                 country, court, judgment_date, content_en,
                 keywords, source, url,
                 ai_status, view_count, favorite_count,
                 created_at, updated_at, is_deleted)
            VALUES
                (%s, %s, %s, %s, %s,
                 %s, %s, %s, %s,
                 %s, %s, %s,
                 0, 0, 0,
                 %s, %s, 0)
        """, (
            case.get("case_no"),
            case.get("title_zh"),
            title_en[:500] if title_en else None,
            case.get("case_reason"),
            case.get("case_type"),
            case.get("country", "USA"),
            case.get("court"),
            judgment_date,
            case.get("content_en"),
            case.get("keywords"),
            case.get("source", "unknown"),
            url[:512],
            now, now,
        ))
        self.conn.commit()
        print(f"[入库] {title_en or url}")
        return True

    @abstractmethod
    def run(self):
        """子类实现具体爬取逻辑"""
        pass

    def start(self):
        """统一入口：连接DB → 执行爬取 → 关闭DB"""
        try:
            self.connect()
            self.run()
        except Exception as e:
            print(f"[错误] 爬虫异常: {e}")
            raise
        finally:
            self.close()
