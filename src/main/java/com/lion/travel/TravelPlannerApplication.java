package com.lion.travel;

import com.lion.travel.common.EnvFileLoader;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 旅游规划助手 MCP Server 启动类。
 *
 * <p>技术栈:Spring Boot 3.5 + Spring AI Alibaba Graph 工作流引擎 + DashScope(通义千问)
 * + MyBatis-Plus + MySQL + Redisson + Hutool,并通过 MCP(SSE)对外暴露能力,
 * 供其他工程(AI Agent / MCP Client)调用。</p>
 *
 * <p>配置:敏感信息通过环境变量注入;若存在项目根目录的 .env 文件,
 * 启动前由 {@link EnvFileLoader} 自动加载(真实环境变量优先,不会覆盖)。</p>
 *
 * @author lion
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.lion.travel.mapper")
@EnableScheduling
public class TravelPlannerApplication {

    public static void main(String[] args) {
        // 在 Spring 容器启动前加载 .env(不存在时静默跳过)
        EnvFileLoader.load();
        ConfigurableApplicationContext context = SpringApplication.run(TravelPlannerApplication.class, args);
        printStartupInfo(context);
    }

    /**
     * 打印简洁的启动信息:应用名称、端口、Web 访问地址、MCP 端点。
     */
    private static void printStartupInfo(ConfigurableApplicationContext context) {
        Environment env = context.getEnvironment();
        String appName = env.getProperty("spring.application.name", "travel-planner");
        String port = env.getProperty("server.port", "8083");
        String baseUrl = "http://localhost:" + port;

        log.info("======================================================");
        log.info("  {} 启动成功", appName);
        log.info("  Web 访问地址 : {}", baseUrl);
        log.info("  MCP 端点     : {}/mcp", baseUrl);
        log.info("======================================================");
    }
}
