package com.lion.travel.graph;

import com.alibaba.cloud.ai.graph.OverAllState;

/**
 * Graph 工作流状态 Key 常量与取值工具。
 *
 * <p>工作流内所有节点通过 OverAllState 共享数据,此处集中定义各字段的 key,
 * 避免散落的魔法字符串。状态流转:</p>
 * <pre>
 *   userInput → requirement → analysis → search → plan → format → result
 * </pre>
 */
public final class GraphStateKeys {

    private GraphStateKeys() {
    }

    /** 用户原始需求文本(工作流输入) */
    public static final String USER_INPUT = "userInput";

    /** 前端请求 id(贯穿工作流,用于 Graph 执行进度打点与轮询查询) */
    public static final String REQUEST_ID = "requestId";

    /** 目标城市 */
    public static final String CITY = "city";

    /** 行程天数 */
    public static final String DAYS = "days";

    /** 预算(元) */
    public static final String BUDGET = "budget";

    /** 出行风格 */
    public static final String STYLE = "style";

    /** 检索到的景点摘要文本(喂给大模型) */
    public static final String ATTRACTIONS_TEXT = "attractionsText";

    /** 检索到的景点数量 */
    public static final String ATTRACTIONS_COUNT = "attractionsCount";

    /** 大模型生成的行程 Markdown */
    public static final String PLAN = "plan";

    /** 最终输出结果(工作流输出) */
    public static final String RESULT = "result";

    /**
     * 从状态安全读取字符串值。
     *
     * @param state 当前状态
     * @param key   字段 key
     * @return 值字符串,缺失时返回空串
     */
    public static String str(OverAllState state, String key) {
        return state.value(key).map(String::valueOf).orElse("");
    }

    /**
     * 从状态安全读取整数。
     *
     * @param state      当前状态
     * @param key        字段 key
     * @param defaultVal 缺失或非法时的默认值
     */
    public static int intVal(OverAllState state, String key, int defaultVal) {
        return state.value(key)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(defaultVal);
    }

    /**
     * 从状态安全读取小数。
     */
    public static double doubleVal(OverAllState state, String key, double defaultVal) {
        return state.value(key)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .orElse(defaultVal);
    }
}
