package com.lion.travel.graph;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户需求解析器。
 *
 * <p>采用「规则优先 + 大模型增强」策略:</p>
 * <ol>
 *   <li>先用轻量规则从需求文本中提取 城市/天数/预算/风格,保证无 API Key 时也能工作;</li>
 *   <li>当规则识别不完整(城市缺失 / 天数缺失 / 预算缺失)且大模型可用时,
 *       调用通义千问补全,再用 LLM 结果覆盖规则结果。</li>
 * </ol>
 *
 * <p>大模型解析失败时静默降级为规则结果,不影响主流程。
 * 可通过 {@code travel.parser.llm-enabled=false} 关闭大模型增强。</p>
 */
@Slf4j
@Component
public class RequirementParser {

    /** 通义千问大模型(由 spring-ai-alibaba-starter-dashscope 自动装配) */
    private final ChatModel chatModel;

    /** 是否启用大模型增强解析(配置 travel.parser.llm-enabled,默认开启) */
    @Value("${travel.parser.llm-enabled:true}")
    private boolean llmEnabled;

    public RequirementParser(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 提取城市:支持 "去XX" "XX玩" "XX旅游" 等句式 */
    private static final Pattern CITY_PATTERN = Pattern.compile("(?:去|到|前往|游玩|玩|旅游)([\\u4e00-\\u9fa5]{2,4}?)(?:玩|旅游|旅行|几天|多少天|吧|呢|\\s|$)");

    /** 天数关键词 */
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s*天");

    /** 预算关键词 */
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d+)\\s*元|预算[\\s:：]*(\\d+)");

    /** 从大模型回复中提取 JSON 对象 */
    private static final Pattern JSON_PATTERN = Pattern.compile("(?s)\\{.*?\\}");

    /** 风格关键词映射 */
    private static final Map<String, String> STYLE_KEYWORDS = new HashMap<>() {{
        put("亲子", "亲子");
        put("带娃", "亲子");
        put("孩子", "亲子");
        put("情侣", "情侣");
        put("蜜月", "情侣");
        put("独自", "独自");
        put("一个人", "独自");
        put("穷游", "穷游");
        put("省钱", "穷游");
        put("老人", "老年");
        put("父母", "老年");
        put("吃货", "美食");
        put("美食", "美食");
        put("休闲", "休闲");
        put("慢节奏", "休闲");
        put("刺激", "冒险");
        put("探险", "冒险");
        put("摄影", "摄影");
        put("拍照", "摄影");
    }};

    /**
     * 解析需求文本,返回结构化字段(Map 的 key 与 GraphStateKeys 一致)。
     *
     * <p>规则优先;规则识别不完整时由大模型补全(失败自动降级为规则结果)。</p>
     *
     * @param input 用户需求文本
     * @return 含 city / days / budget / style 的 Map,未识别字段为空或默认值
     */
    public Map<String, Object> parse(String input) {
        String text = StrUtil.nullToEmpty(input).trim();
        Map<String, Object> result = parseByRule(text);

        // 规则识别不完整且大模型可用时,用 LLM 增强补全
        if (llmEnabled && needEnhance(text, result)) {
            try {
                Map<String, Object> llmResult = parseByLlm(text);
                overlay(result, llmResult);
            } catch (Exception e) {
                log.warn("大模型解析需求失败,沿用规则结果: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 纯规则解析(不调用大模型),供特殊场景使用。
     *
     * @param input 用户需求文本
     * @return 含 city / days / budget / style 的 Map,未识别字段为空或默认值
     */
    public static Map<String, Object> parseByRule(String input) {
        Map<String, Object> result = new HashMap<>();
        String text = StrUtil.nullToEmpty(input).trim();

        // 1. 城市
        String city = extractCity(text);
        result.put(GraphStateKeys.CITY, StrUtil.nullToEmpty(city));

        // 2. 天数
        int days = extractDays(text);
        result.put(GraphStateKeys.DAYS, days > 0 ? days : 3);

        // 3. 预算
        double budget = extractBudget(text);
        result.put(GraphStateKeys.BUDGET, budget > 0 ? budget : 2000);

        // 4. 风格
        result.put(GraphStateKeys.STYLE, extractStyle(text));

        return result;
    }

    /**
     * 判断规则解析是否不完整(需要大模型补全)。
     * 规则总能给出风格默认值,因此只看 城市/天数/预算 是否缺失。
     */
    private boolean needEnhance(String text, Map<String, Object> result) {
        return StrUtil.isBlank((String) result.get(GraphStateKeys.CITY))
                || extractDays(text) == 0
                || extractBudget(text) == 0;
    }

    /** 调用大模型解析需求,返回结构化 Map;失败由调用方兜底 */
    private Map<String, Object> parseByLlm(String text) {
        String prompt = """
                你是旅游需求解析助手。请从用户需求中提取结构化信息,只输出 JSON,不要任何其他文字:
                {"city":"城市名","days":天数数字,"budget":预算数字,"style":"出行风格"}

                用户需求:%s
                """.formatted(text);
        String resp = chatModel.call(prompt);
        JSONObject json = JSONUtil.parseObj(extractJson(resp));

        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKeys.CITY, StrUtil.nullToEmpty(json.getStr("city", "")));
        result.put(GraphStateKeys.DAYS, json.getInt("days", 0));
        result.put(GraphStateKeys.BUDGET, json.getDouble("budget", 0.0));
        result.put(GraphStateKeys.STYLE, StrUtil.nullToEmpty(json.getStr("style", "")));
        log.info("大模型解析需求成功:city={}, days={}, budget={}, style={}",
                result.get(GraphStateKeys.CITY), result.get(GraphStateKeys.DAYS),
                result.get(GraphStateKeys.BUDGET), result.get(GraphStateKeys.STYLE));
        return result;
    }

    /** 用大模型结果覆盖规则结果(仅覆盖 LLM 识别出的非空值) */
    private static void overlay(Map<String, Object> rule, Map<String, Object> llm) {
        Object city = llm.get(GraphStateKeys.CITY);
        if (city instanceof String s && StrUtil.isNotBlank(s)) {
            rule.put(GraphStateKeys.CITY, s);
        }
        Object days = llm.get(GraphStateKeys.DAYS);
        if (days instanceof Integer d && d > 0) {
            rule.put(GraphStateKeys.DAYS, d);
        }
        Object budget = llm.get(GraphStateKeys.BUDGET);
        if (budget instanceof Number b && b.doubleValue() > 0) {
            rule.put(GraphStateKeys.BUDGET, b.doubleValue());
        }
        Object style = llm.get(GraphStateKeys.STYLE);
        if (style instanceof String s && StrUtil.isNotBlank(s)) {
            rule.put(GraphStateKeys.STYLE, s);
        }
    }

    /** 从模型回复中提取第一个 JSON 对象;提取失败返回空对象 */
    private static String extractJson(String text) {
        if (StrUtil.isBlank(text)) {
            return "{}";
        }
        Matcher m = JSON_PATTERN.matcher(text);
        return m.find() ? m.group() : "{}";
    }

    /** 提取城市 */
    private static String extractCity(String text) {
        Matcher m = CITY_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        // 常见城市直接命中
        for (String city : new String[]{"北京", "上海", "广州", "深圳", "杭州", "成都", "西安", "重庆", "南京", "苏州", "武汉", "长沙", "厦门", "青岛", "天津", "昆明", "大理", "丽江"}) {
            if (text.contains(city)) {
                return city;
            }
        }
        return "";
    }

    /** 提取天数 */
    private static int extractDays(String text) {
        Matcher m = DAYS_PATTERN.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    /** 提取预算 */
    private static double extractBudget(String text) {
        Matcher m = BUDGET_PATTERN.matcher(text);
        if (m.find()) {
            if (m.group(1) != null) {
                return Double.parseDouble(m.group(1));
            }
            if (m.group(2) != null) {
                return Double.parseDouble(m.group(2));
            }
        }
        return 0;
    }

    /** 提取风格 */
    private static String extractStyle(String text) {
        for (Map.Entry<String, String> e : STYLE_KEYWORDS.entrySet()) {
            if (text.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return "常规";
    }
}
