package com.lion.travel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger)在线接口文档配置。
 *
 * <p>引入 springdoc-openapi 后,浏览器访问 {@code /swagger-ui.html} 即可查看与调试 REST 接口;
 * OpenAPI 描述文件(JSON)位于 {@code /v3/api-docs}。接口扫描为自动完成,此处仅配置文档展示信息。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 文档标题 / 描述 / 版本等展示信息。
     */
    @Bean
    public OpenAPI travelPlannerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Lion MCP Travel Planner REST API")
                .description("""
                        旅游规划助手 MCP Server 的 REST 接口文档。

                        - 前端页面: / (HTML 演示页)
                        - MCP Server(Streamable HTTP): POST /mcp(供 MCP Client 调用)
                        - 本在线文档: /swagger-ui.html
                        """)
                .version("1.0.0"));
    }
}
