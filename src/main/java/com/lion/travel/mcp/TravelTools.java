package com.lion.travel.mcp;

import com.lion.travel.entity.Attraction;
import com.lion.travel.service.AiTravelService;
import com.lion.travel.service.AttractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 工具定义:对外暴露给另一个工程(AI Agent / MCP Client)调用。
 *
 * <p>本类中的每个 {@code @Tool} 方法都会注册为 MCP Server 的一个工具,
 * 调用方通过 SSE 端点(默认 /sse)发现并调用。工具列表:</p>
 * <ul>
 *   <li>{@link #searchAttractions} 搜索景点</li>
 *   <li>{@link #generateTravelPlan} 生成旅行计划(走 Graph 工作流)</li>
 *   <li>{@link #listCities} 列出可选城市</li>
 * </ul>
 */
@Slf4j
@Component
public class TravelTools {

    private final AttractionService attractionService;
    private final AiTravelService aiTravelService;

    /**
     * 手写构造器(而非 Lombok @RequiredArgsConstructor):
     * {@link AiTravelService} 依赖 Graph -> ChatModel -> ToolCallingManager -> 本类,存在循环依赖,
     * 因此给 aiTravelService 加 {@link Lazy},注入代理打断循环;
     * 真正调用工具方法时容器已完成初始化,再解析完整 Bean。
     */
    public TravelTools(AttractionService attractionService, @Lazy AiTravelService aiTravelService) {
        this.attractionService = attractionService;
        this.aiTravelService = aiTravelService;
    }

    /**
     * 搜索旅游景点:按城市与关键词检索数据库中的景点信息。
     */
    @Tool(description = "搜索旅游景点,支持按城市和关键词过滤,返回景点名称、评分、人均消费、简介等")
    public List<Attraction> searchAttractions(
            @ToolParam(description = "城市名称,例如:北京、上海、成都;为空表示不限城市") String city,
            @ToolParam(description = "搜索关键词,例如:亲子、夜景、免费;为空表示不限制") String keyword) {
        return attractionService.search(city, keyword);
    }

    /**
     * 生成旅行计划:一句话描述需求,通过 Graph 工作流编排大模型生成完整行程规划。
     */
    @Tool(description = "根据一句话旅行需求生成完整行程规划,例如:'北京3天亲子游,预算5000元',返回 Markdown 格式的每日行程安排")
    public String generateTravelPlan(
            @ToolParam(description = "用户的旅行需求描述,建议包含城市、天数、预算、出行风格等信息") String requirements) {
        return aiTravelService.generateTravelPlan(requirements);
    }

    /**
     * 列出系统内置的旅游城市,便于调用方引导用户选择。
     */
    @Tool(description = "列出内置支持的旅游城市列表,用于引导用户选择目的地")
    public List<String> listCities() {
        return attractionService.listCities();
    }
}
