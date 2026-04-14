-- 案例检索：全文索引改为 ngram（中文按 ngram_token_size 元组切分），并纳入 summary_zh
-- 要求 MySQL >= 5.7.6；可选在 my.cnf 设置 ngram_token_size=2 后重启再建索引
-- 已有库执行本脚本；全新建库使用 init.sql 即可

ALTER TABLE `legal_case` DROP INDEX `ft_search`;

ALTER TABLE `legal_case`
    ADD FULLTEXT INDEX `ft_search` (
        `title_zh`,
        `title_en`,
        `case_reason`,
        `keywords`,
        `summary_zh`
    ) WITH PARSER ngram;
