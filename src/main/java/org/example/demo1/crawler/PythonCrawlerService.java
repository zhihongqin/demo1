package org.example.demo1.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Python 爬虫进程管理服务
 * 负责启动、停止和监控各网站的 Python 爬虫脚本
 */
@Slf4j
@Service
public class PythonCrawlerService {

    /** 正在运行的爬虫进程，key = 爬虫名（如 site_a） */
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    /** Python 脚本所在目录（相对于 Spring Boot 启动目录，或使用绝对路径） */
    @Value("${crawler.python.script-dir:crawlers}")
    private String scriptDir;

    /** Python 可执行文件（Windows 用 python，Linux/Mac 用 python3） */
    @Value("${crawler.python.executable:python}")
    private String pythonExecutable;

    /**
     * 异步启动指定爬虫（无额外参数）
     *
     * @param crawlerName 爬虫名，对应脚本文件 {scriptDir}/{crawlerName}_crawler.py
     */
    @Async
    public void start(String crawlerName) {
        startWithArgs(crawlerName, List.of());
    }

    /**
     * 异步启动指定爬虫，并向脚本传入额外命令行参数
     *
     * @param crawlerName 爬虫名，对应脚本文件 {scriptDir}/{crawlerName}_crawler.py
     * @param extraArgs   追加到 python script.py 之后的参数列表，如 ["--query1", "中華", "--max-pages", "20"]
     */
    @Async
    public void startWithArgs(String crawlerName, List<String> extraArgs) {
        if (isRunning(crawlerName)) {
            log.warn("[Python爬虫] {} 已在运行中，忽略本次启动请求", crawlerName);
            return;
        }

        String scriptPath = scriptDir + File.separator + crawlerName + "_crawler.py";
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            log.error("[Python爬虫] 脚本文件不存在: {}", scriptFile.getAbsolutePath());
            return;
        }

        // 确保日志目录存在
        File logDir = new File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        File logFile = new File("logs/crawler_" + crawlerName + ".log");

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(pythonExecutable);
            cmd.add(scriptFile.getAbsolutePath());
            if (extraArgs != null) {
                cmd.addAll(extraArgs);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

            Process process = pb.start();
            runningProcesses.put(crawlerName, process);
            log.info("[Python爬虫] {} 已启动，pid={}, args={}, 日志={}",
                    crawlerName, process.pid(), extraArgs, logFile.getAbsolutePath());

            // 进程结束后自动从 map 中移除
            process.onExit().thenAccept(p -> {
                runningProcesses.remove(crawlerName);
                log.info("[Python爬虫] {} 已结束，exitCode={}", crawlerName, p.exitValue());
            });

        } catch (IOException e) {
            log.error("[Python爬虫] {} 启动失败: {}", crawlerName, e.getMessage());
        }
    }

    /**
     * 停止指定爬虫进程
     *
     * @param crawlerName 爬虫名
     * @return true=成功停止，false=未找到运行中的进程
     */
    public boolean stop(String crawlerName) {
        Process process = runningProcesses.get(crawlerName);
        if (process == null || !process.isAlive()) {
            log.warn("[Python爬虫] {} 未在运行", crawlerName);
            return false;
        }
        process.destroy();
        runningProcesses.remove(crawlerName);
        log.info("[Python爬虫] {} 已停止", crawlerName);
        return true;
    }

    /**
     * 判断指定爬虫是否正在运行
     */
    public boolean isRunning(String crawlerName) {
        Process p = runningProcesses.get(crawlerName);
        return p != null && p.isAlive();
    }

    /**
     * 获取脚本目录下所有可用爬虫及其运行状态
     * key = 爬虫名，value = 是否正在运行
     */
    public Map<String, Boolean> getAllStatus() {
        Map<String, Boolean> statusMap = new LinkedHashMap<>();
        File dir = new File(scriptDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] scripts = dir.listFiles(f -> f.getName().endsWith("_crawler.py"));
            if (scripts != null) {
                for (File script : scripts) {
                    String name = script.getName().replace("_crawler.py", "");
                    statusMap.put(name, isRunning(name));
                }
            }
        }
        return statusMap;
    }
}
