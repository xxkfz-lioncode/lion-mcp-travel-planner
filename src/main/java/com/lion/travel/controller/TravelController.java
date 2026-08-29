package com.lion.travel.controller;

import com.lion.travel.common.Result;
import com.lion.travel.dto.GeneratePlanRequest;
import com.lion.travel.entity.Attraction;
import com.lion.travel.entity.Itinerary;
import com.lion.travel.graph.GraphProgress;
import com.lion.travel.service.AiTravelService;
import com.lion.travel.service.AttractionService;
import com.lion.travel.service.ItineraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 旅游规划 REST 接口(Spring MVC 同步风格)。
 *
 * <p>供 HTML 前端页面调用;与 MCP 工具层能力互补,
 * MCP 面向另一工程(Agent 自动调用),REST 面向浏览器用户。</p>
 *
 * <p>项目基于 Servlet 栈(Tomcat),请求线程允许阻塞,
 * 因此 MyBatis / Redisson / 大模型调用直接同步执行,无需响应式包装。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TravelController {

    private final AttractionService attractionService;
    private final ItineraryService itineraryService;
    private final AiTravelService aiTravelService;
    private final GraphProgress graphProgress;

    /**
     * 检索景点:GET /api/attractions?city=北京&keyword=亲子
     */
    @GetMapping("/attractions")
    public Result<List<Attraction>> searchAttractions(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword) {
        return Result.ok(attractionService.search(city, keyword));
    }

    /**
     * 城市列表:GET /api/cities
     */
    @GetMapping("/cities")
    public Result<List<String>> listCities() {
        return Result.ok(attractionService.listCities());
    }

    /**
     * 生成旅行计划:POST /api/itinerary/generate
     * 请求体可携带 requestId,前端据此轮询 Graph 执行进度
     */
    @PostMapping("/itinerary/generate")
    public Result<String> generatePlan(@Valid @RequestBody GeneratePlanRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            return Result.ok(aiTravelService.generateTravelPlan(request.getRequirements()));
        }
        return Result.ok(aiTravelService.generateTravelPlan(request.getRequirements(), request.getRequestId()));
    }

    /**
     * Graph 执行进度:GET /api/graph/progress?requestId=xxx
     * 返回各节点执行状态(按执行顺序),供前端流程图实时展示
     */
    @GetMapping("/graph/progress")
    public Result<java.util.List<GraphProgress.NodeState>> graphProgress(@RequestParam String requestId) {
        return Result.ok(graphProgress.get(requestId));
    }

    /**
     * 历史行程:GET /api/itinerary/history?city=北京
     */
    @GetMapping("/itinerary/history")
    public Result<List<Itinerary>> itineraryHistory(
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.ok(itineraryService.list(city, limit));
    }

    /**
     * 健康检查:GET /api/health,用于调用方探测服务可用性
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("travel-planner service is running");
    }
}
