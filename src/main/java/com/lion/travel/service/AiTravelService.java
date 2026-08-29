package com.lion.travel.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.lion.travel.common.exception.BusinessException;
import com.lion.travel.entity.Itinerary;
import com.lion.travel.graph.GraphProgress;
import com.lion.travel.graph.GraphStateKeys;
import com.lion.travel.graph.RequirementParser;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * AI 旅行服务:Graph 工作流编排入口。
 *
 * <p>对外(MCP 工具 / REST 接口)提供「一句话生成旅行计划」能力,内部:</p>
 * <ol>
 *   <li>优先命中 Redis 缓存(相同城市+天数+风格直接返回)</li>
 *   <li>Redisson 分布式锁防止并发生成重复调用大模型</li>
 *   <li>调用 {@link CompiledGraph} 执行完整工作流</li>
 *   <li>结果持久化到 MySQL 并写入缓存</li>
 * </ol>
 *
 * <p>说明:项目基于 Spring MVC(Servlet 栈),请求线程允许阻塞,
 * 因此直接同步调用 {@code travelGraph.invoke()},无需线程池与响应式包装;
 * 调用耗时的上限由 RestClientConfig 的 300s 读取超时兜底。</p>
 *
 * <p>进度:前端传入 {@code requestId} 会随 Graph 状态贯穿工作流,
 * 各节点执行时通过 {@link GraphProgress} 打点,前端轮询实时展示流程图。</p>
 */
@Slf4j
@Service
public class AiTravelService {

    private final CompiledGraph travelGraph;
    private final ItineraryService itineraryService;
    private final RedissonClient redissonClient;
    private final GraphProgress graphProgress;
    private final RequirementParser requirementParser;

    public AiTravelService(CompiledGraph travelGraph,
                           ItineraryService itineraryService,
                           RedissonClient redissonClient,
                           GraphProgress graphProgress,
                           RequirementParser requirementParser) {
        this.travelGraph = travelGraph;
        this.itineraryService = itineraryService;
        this.redissonClient = redissonClient;
        this.graphProgress = graphProgress;
        this.requirementParser = requirementParser;
    }

    /** 分布式锁前缀 */
    private static final String LOCK_PREFIX = "travel:lock:";

    /**
     * 根据用户需求生成旅行计划(自动生成 requestId,供 MCP 工具等不关心进度的调用方使用)。
     *
     * @param requirements 用户需求,如 "北京3天亲子游,预算5000元"
     * @return 行程 Markdown 文本
     */
    public String generateTravelPlan(String requirements) {
        return generateTravelPlan(requirements, graphProgress.begin());
    }

    /**
     * 根据用户需求生成旅行计划(指定 requestId,前端可据此轮询执行进度)。
     *
     * @param requirements 用户需求
     * @param requestId    前端生成的请求 id
     * @return 行程 Markdown 文本
     */
    public String generateTravelPlan(String requirements, String requestId) {
        if (StrUtil.isBlank(requirements)) {
            throw new BusinessException(400, "旅行需求不能为空");
        }
        // 初始化进度容器(前端轮询即可看到各节点状态)
        graphProgress.begin(requestId);

        // 规则+大模型增强解析需求,得到用于缓存与加锁的维度
        Map<String, Object> parsed = requirementParser.parse(requirements);
        String city = StrUtil.nullToEmpty((String) parsed.get(GraphStateKeys.CITY));
        int days = (int) parsed.get(GraphStateKeys.DAYS);
        double budget = (double) parsed.get(GraphStateKeys.BUDGET);
        String style = StrUtil.nullToEmpty((String) parsed.get(GraphStateKeys.STYLE));

        // 1. 缓存命中:直接返回历史结果,节省大模型成本
        String cached = itineraryService.getCachedContent(city, days, style);
        if (cached != null) {
            log.info("行程命中缓存:city={}, days={}, style={}", city, days, style);
            graphProgress.finish(requestId);
            return cached;
        }

        // 2. 分布式锁:同一维度只允许一个请求生成,避免重复调用大模型
        RLock lock = redissonClient.getLock(LOCK_PREFIX + city + ":" + days + ":" + style);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 60, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取行程生成锁超时:city={}, days={}", city, days);
            }
            // 双检缓存(等待期间其他线程可能已生成)
            String recheck = itineraryService.getCachedContent(city, days, style);
            if (recheck != null) {
                graphProgress.finish(requestId);
                return recheck;
            }

            // 3. 同步执行 Graph 工作流(requestId 随状态贯穿,节点用于进度打点)
            Map<String, Object> input = new HashMap<>();
            input.put(GraphStateKeys.USER_INPUT, requirements);
            input.put(GraphStateKeys.REQUEST_ID, requestId);
            Optional<OverAllState> result = travelGraph.invoke(input);
            String content = result.map(s -> GraphStateKeys.str(s, GraphStateKeys.RESULT))
                    .filter(StrUtil::isNotBlank)
                    .orElseThrow(() -> new BusinessException("行程生成失败,请稍后重试"));
            graphProgress.finish(requestId);

            // 4. 持久化行程(内部同时写入缓存)
            Itinerary itinerary = new Itinerary();
            itinerary.setCity(city);
            itinerary.setDays(days);
            itinerary.setBudget(budget);
            itinerary.setStyle(style);
            itinerary.setContent(content);
            itineraryService.save(itinerary);
            log.info("行程生成完成:city={}, days={}, style={}", city, days, style);
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("行程生成异常", e);
            throw new BusinessException("行程生成异常:" + e.getMessage());
        } finally {
            // 释放锁(仅当前线程持有且未被中断时)
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
