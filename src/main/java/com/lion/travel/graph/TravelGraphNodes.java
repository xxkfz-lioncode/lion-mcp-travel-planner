package com.lion.travel.graph;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.lion.travel.entity.Attraction;
import com.lion.travel.service.AttractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.lion.travel.graph.GraphStateKeys.ATTRACTIONS_COUNT;
import static com.lion.travel.graph.GraphStateKeys.ATTRACTIONS_TEXT;
import static com.lion.travel.graph.GraphStateKeys.BUDGET;
import static com.lion.travel.graph.GraphStateKeys.CITY;
import static com.lion.travel.graph.GraphStateKeys.DAYS;
import static com.lion.travel.graph.GraphStateKeys.PLAN;
import static com.lion.travel.graph.GraphStateKeys.REQUEST_ID;
import static com.lion.travel.graph.GraphStateKeys.RESULT;
import static com.lion.travel.graph.GraphStateKeys.STYLE;
import static com.lion.travel.graph.GraphStateKeys.USER_INPUT;
import static com.lion.travel.graph.GraphStateKeys.doubleVal;
import static com.lion.travel.graph.GraphStateKeys.intVal;
import static com.lion.travel.graph.GraphStateKeys.str;

/**
 * Graph 工作流节点实现。
 *
 * <p>每个方法对应一个图节点,接收 {@link OverAllState},返回要写入状态的 Map。
 * 节点职责:</p>
 * <ul>
 *   <li>{@link #requirement}  需求解析(规则提取城市/天数/预算/风格)</li>
 *   <li>{@link #analysis}     偏好分析(规则未识别时用大模型补全)</li>
 *   <li>{@link #search}       景点检索(查 MySQL,组装文本喂给大模型)</li>
 *   <li>{@link #plan}         行程生成(大模型生成 Markdown,失败降级本地模板)</li>
 *   <li>{@link #format}       格式化输出最终结果</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TravelGraphNodes {

    /** 通义千问大模型(由 spring-ai-alibaba-starter-dashscope 自动装配) */
    private final ChatModel chatModel;

    /** 景点检索服务 */
    private final AttractionService attractionService;

    /** Graph 执行进度记录器(供前端流程图展示节点状态) */
    private final GraphProgress graphProgress;

    /** 用户需求解析器(规则优先,识别不完整时大模型增强补全) */
    private final RequirementParser requirementParser;

    /** 是否启用 AI 生成(配置 travel.ai-enabled,false 时走本地模板) */
    @Value("${travel.ai-enabled:true}")
    private boolean aiEnabled;

    /** 节点名称常量(与 TravelGraphConfig 注册名一致,用于进度打点) */
    private static final String NODE_REQUIREMENT = "requirement";
    private static final String NODE_ANALYSIS = "analysis";
    private static final String NODE_SEARCH = "search";
    private static final String NODE_PLAN = "plan";
    private static final String NODE_FORMAT = "format";

    /** 行程生成提示词(让大模型产出结构化、可执行的行程) */
    private static final String PLAN_PROMPT_TEMPLATE = """
            你是一位资深旅行规划师。请根据以下信息为用户生成一份详细的旅行行程规划。

            ## 用户需求
            %s

            ## 出行参数
            - 城市:%s
            - 天数:%d 天
            - 预算:%.0f 元
            - 风格:%s

            ## 可参考的景点(按评分排序)
            %s

            ## 输出要求
            1. 按"第1天 / 第2天 ..."组织,每天包含上午、下午、晚上安排
            2. 每个景点给出推荐游玩时长
            3. 每天结尾注明大致餐饮与住宿建议
            4. 最后附"行程小贴士"(交通、穿搭、避坑建议)
            5. 使用 Markdown 格式,语言简洁实用
            """;

    /**
     * 节点:需求解析。
     * 用规则从原始需求中提取结构化参数;规则解析保证离线可用。
     */
    public Map<String, Object> requirement(OverAllState state) {
        String requestId = str(state, REQUEST_ID);
        graphProgress.mark(requestId, NODE_REQUIREMENT, "需求解析", GraphProgress.RUNNING);
        try {
            String input = str(state, USER_INPUT);
            Map<String, Object> parsed = requirementParser.parse(input);
            // 解析结果写入状态(与已有值合并,保留其他节点的写入)
            return Map.of(
                    USER_INPUT, input,
                    CITY, parsed.get(CITY),
                    DAYS, parsed.get(DAYS),
                    BUDGET, parsed.get(BUDGET),
                    STYLE, parsed.get(STYLE)
            );
        } finally {
            graphProgress.mark(requestId, NODE_REQUIREMENT, "需求解析", GraphProgress.DONE);
        }
    }

    /**
     * 节点:偏好分析。
     * 规则未识别出城市时,借助大模型从需求文本补全城市/天数/预算/风格。
     * 大模型不可用时静默降级,不影响主流程。
     */
    public Map<String, Object> analysis(OverAllState state) {
        String requestId = str(state, REQUEST_ID);
        graphProgress.mark(requestId, NODE_ANALYSIS, "偏好分析", GraphProgress.RUNNING);
        try {
            String input = str(state, USER_INPUT);
            String city = str(state, CITY);
            // 已有城市或未启用 AI:直接透传,不再调用大模型
            if (StrUtil.isNotBlank(city) || !aiEnabled) {
                return Map.of(CITY, city);
            }
            try {
                String prompt = """
                        你是旅游需求解析助手。请从用户需求中提取结构化信息,只输出 JSON,不要任何其他文字:
                        {"city":"城市名","days":天数数字,"budget":预算数字,"style":"出行风格"}

                        用户需求:%s
                        """.formatted(input);
                String resp = chatModel.call(prompt);
                JSONObject json = JSONUtil.parseObj(extractJson(resp));
                String parsedCity = StrUtil.nullToEmpty(json.getStr("city", ""));
                int days = json.getInt("days", 3);
                double budget = json.getDouble("budget", 2000.0);
                String style = StrUtil.nullToEmpty(json.getStr("style", "常规"));
                log.info("大模型解析需求:city={}, days={}, budget={}, style={}", parsedCity, days, budget, style);
                return Map.of(CITY, parsedCity, DAYS, days, BUDGET, budget, STYLE, style);
            } catch (Exception e) {
                log.warn("大模型偏好解析失败,沿用默认值: {}", e.getMessage());
                return Map.of(CITY, city);
            }
        } finally {
            graphProgress.mark(requestId, NODE_ANALYSIS, "偏好分析", GraphProgress.DONE);
        }
    }

    /**
     * 节点:景点检索。
     * 按城市查询景点(默认取评分前 8),组装为文本供大模型参考。
     */
    public Map<String, Object> search(OverAllState state) {
        String requestId = str(state, REQUEST_ID);
        graphProgress.mark(requestId, NODE_SEARCH, "景点检索", GraphProgress.RUNNING);
        try {
            String city = str(state, CITY);
            List<Attraction> attractions = StrUtil.isNotBlank(city)
                    ? attractionService.searchTopByCity(city, 8)
                    : attractionService.search(null, null).stream().limit(8).toList();

            String text = attractions.stream()
                    .map(a -> String.format("- %s(%s|评分%.1f|人均%.0f元|开放%s):%s",
                            a.getName(), a.getCategory(), a.getRating(), a.getPrice(),
                            StrUtil.nullToEmpty(a.getOpenTime()), StrUtil.nullToEmpty(a.getDescription())))
                    .collect(Collectors.joining("\n"));
            return Map.of(ATTRACTIONS_TEXT, StrUtil.nullToEmpty(text), ATTRACTIONS_COUNT, attractions.size());
        } finally {
            graphProgress.mark(requestId, NODE_SEARCH, "景点检索", GraphProgress.DONE);
        }
    }

    /**
     * 节点:行程生成。
     * 优先调用大模型生成行程;无 API Key 或调用失败时,降级为本地模板行程。
     */
    public Map<String, Object> plan(OverAllState state) {
        String requestId = str(state, REQUEST_ID);
        graphProgress.mark(requestId, NODE_PLAN, "行程生成", GraphProgress.RUNNING);
        try {
            String input = str(state, USER_INPUT);
            String city = str(state, CITY);
            int days = intVal(state, DAYS, 3);
            double budget = doubleVal(state, BUDGET, 2000);
            String style = str(state, STYLE);
            String attractionsText = str(state, ATTRACTIONS_TEXT);

            // 优先大模型
            if (aiEnabled) {
                try {
                    String prompt = PLAN_PROMPT_TEMPLATE.formatted(input, city, days, budget, style,
                            StrUtil.isBlank(attractionsText) ? "(暂无数据,请给出通用建议)" : attractionsText);
                    String plan = chatModel.call(prompt);
                    if (StrUtil.isNotBlank(plan)) {
                        return Map.of(PLAN, plan.trim());
                    }
                } catch (Exception e) {
                    log.warn("大模型生成行程失败,降级本地模板: {}", e.getMessage());
                }
            }
            return Map.of(PLAN, buildLocalPlan(city, days, style, attractionsText));
        } finally {
            graphProgress.mark(requestId, NODE_PLAN, "行程生成", GraphProgress.DONE);
        }
    }

    /**
     * 节点:格式化输出。
     * 组装最终返回文本(标题 + 行程 + 说明)。
     */
    public Map<String, Object> format(OverAllState state) {
        String requestId = str(state, REQUEST_ID);
        graphProgress.mark(requestId, NODE_FORMAT, "格式化", GraphProgress.RUNNING);
        try {
            String city = str(state, CITY);
            int days = intVal(state, DAYS, 3);
            String style = str(state, STYLE);
            String plan = str(state, PLAN);

            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(StrUtil.isBlank(city) ? "热门城市" : city).append(" ").append(days).append(" 日游规划\n\n");
            sb.append("> 出行风格:").append(StrUtil.blankToDefault(style, "常规")).append("\n\n");
            sb.append(plan).append("\n");
            sb.append("\n---\n").append("> 本行程由旅游规划助手 AI 生成,仅供参考,实际出行请以官方信息为准。");
            return Map.of(RESULT, sb.toString());
        } finally {
            graphProgress.mark(requestId, NODE_FORMAT, "格式化", GraphProgress.DONE);
        }
    }

    /**
     * 本地模板行程:无大模型时的兜底方案,保证功能可演示。
     */
    private String buildLocalPlan(String city, int days, String style, String attractionsText) {
        String target = StrUtil.isBlank(city) ? "目的地" : city;
        StringBuilder sb = new StringBuilder();
        for (int day = 1; day <= Math.max(1, days); day++) {
            sb.append("## 第 ").append(day).append(" 天\n");
            sb.append("- **上午**:游览").append(target).append("核心景点(参考下方推荐列表)\n");
            sb.append("- **下午**:继续探索").append(target).append("特色街区与文化场所\n");
            sb.append("- **晚上**:品尝当地美食,感受城市夜生活\n\n");
        }
        sb.append("### 推荐景点\n");
        sb.append(StrUtil.isBlank(attractionsText) ? "- 暂无具体景点数据,建议到达后咨询当地旅游服务中心\n" : attractionsText + "\n");
        sb.append("\n### 行程小贴士\n");
        sb.append("- 提前预订酒店与热门景点门票\n");
        sb.append("- 关注当地天气,准备合适衣物\n");
        sb.append("- 预留弹性时间,行程不要排太满\n");
        return sb.toString();
    }

    /**
     * 从大模型回复中提取 JSON 片段(兼容 ```json 代码块包裹)。
     */
    private String extractJson(String text) {
        if (StrUtil.isBlank(text)) {
            return "{}";
        }
        Pattern pattern = Pattern.compile("\\{.*}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : text;
    }
}
