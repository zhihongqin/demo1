package org.example.demo1.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 日本裁判所爬虫启动参数 DTO
 * 对应 POST /api/admin/crawler/python/start-japan 请求体
 * 各字段与 japan_courts_crawler.py 的命令行参数一一对应
 */
@Data
public class JapanCrawlerParamDTO {

    /** 第1检索关键词（必填）*/
    private String query1 = "中華";

    /** 第2检索关键词（可选）*/
    private String query2;

    // ── 判决日期范围 ──────────────────────────────────────────────────────────
    /** 判决日期起・元号（明治/大正/昭和/平成/令和）*/
    private String judgeGengoFrom;
    /** 判决日期起・年 */
    private String judgeYearFrom;
    /** 判决日期起・月 */
    private String judgeMonthFrom;
    /** 判决日期起・日 */
    private String judgeDayFrom;

    /** 判决日期止・元号 */
    private String judgeGengoTo;
    /** 判决日期止・年 */
    private String judgeYearTo;
    /** 判决日期止・月 */
    private String judgeMonthTo;
    /** 判决日期止・日 */
    private String judgeDayTo;

    // ── 法院过滤 ──────────────────────────────────────────────────────────────
    /** 裁判所种别（最高裁判所/高等裁判所/地方裁判所/家庭裁判所/簡易裁判所）*/
    private String courtType;
    /** 裁判所名 */
    private String courtName;
    /** 支部名 */
    private String branchName;

    // ── 采集控制 ──────────────────────────────────────────────────────────────
    /** 最大爬取页数，默认 50 */
    private Integer maxPages = 50;

    // ── 数据库连接（可选，不填则使用爬虫默认配置）────────────────────────────
    private String dbHost;
    private Integer dbPort;
    private String dbUser;
    private String dbPassword;
    private String dbName;

    /**
     * 将 DTO 转换为 Python 命令行参数列表
     * 只输出非空字段，空字段由 Python 脚本使用默认值
     */
    public List<String> toArgList() {
        List<String> args = new ArrayList<>();

        addArg(args, "--query1",            query1);
        addArg(args, "--query2",            query2);
        addArg(args, "--judge-gengo-from",  judgeGengoFrom);
        addArg(args, "--judge-year-from",   judgeYearFrom);
        addArg(args, "--judge-month-from",  judgeMonthFrom);
        addArg(args, "--judge-day-from",    judgeDayFrom);
        addArg(args, "--judge-gengo-to",    judgeGengoTo);
        addArg(args, "--judge-year-to",     judgeYearTo);
        addArg(args, "--judge-month-to",    judgeMonthTo);
        addArg(args, "--judge-day-to",      judgeDayTo);
        addArg(args, "--court-type",        courtType);
        addArg(args, "--court-name",        courtName);
        addArg(args, "--branch-name",       branchName);

        if (maxPages != null && maxPages > 0) {
            args.add("--max-pages");
            args.add(String.valueOf(maxPages));
        }

        addArg(args, "--db-host",     dbHost);
        if (dbPort != null) { args.add("--db-port"); args.add(String.valueOf(dbPort)); }
        addArg(args, "--db-user",     dbUser);
        addArg(args, "--db-password", dbPassword);
        addArg(args, "--db-name",     dbName);

        return args;
    }

    private void addArg(List<String> args, String flag, String value) {
        if (value != null && !value.isBlank()) {
            args.add(flag);
            args.add(value);
        }
    }
}
