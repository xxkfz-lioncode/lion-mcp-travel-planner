package com.lion.travel.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Graph 工作流定义。
 *
 * <p>使用 spring-ai-alibaba-graph-core 的 {@link StateGraph} 将旅游规划拆解为
 * 有序节点流水线,编译为 {@link CompiledGraph} 供业务层调用。流程:</p>
 *
 * <pre>
 * START → 需求解析(requirement) → 偏好分析(analysis) → 景点检索(search)
 *       → 行程生成(plan) → 格式化输出(format) → END
 * </pre>
 *
 * <p>每个节点通过 {@code node_async} 将同步逻辑包装为异步节点动作,
 * 节点间通过 OverAllState 共享数据。</p>
 */
@Configuration
public class TravelGraphConfig {

    /** 节点名称常量 */
    private static final String NODE_REQUIREMENT = "requirement";
    private static final String NODE_ANALYSIS = "analysis";
    private static final String NODE_SEARCH = "search";
    private static final String NODE_PLAN = "plan";
    private static final String NODE_FORMAT = "format";

    /**
     * 构建并编译旅游规划工作流。
     *
     * @param nodes 节点实现组件(依赖 ChatModel / AttractionService 等)
     * @return 编译后的可执行图
     */
    @Bean
    public CompiledGraph travelGraph(TravelGraphNodes nodes) throws GraphStateException {
        StateGraph graph = new StateGraph()
                // ---- 注册节点 ----
                .addNode(NODE_REQUIREMENT, node_async(nodes::requirement))
                .addNode(NODE_ANALYSIS, node_async(nodes::analysis))
                .addNode(NODE_SEARCH, node_async(nodes::search))
                .addNode(NODE_PLAN, node_async(nodes::plan))
                .addNode(NODE_FORMAT, node_async(nodes::format))
                // ---- 连接边 ----
                .addEdge(START, NODE_REQUIREMENT)
                .addEdge(NODE_REQUIREMENT, NODE_ANALYSIS)
                .addEdge(NODE_ANALYSIS, NODE_SEARCH)
                .addEdge(NODE_SEARCH, NODE_PLAN)
                .addEdge(NODE_PLAN, NODE_FORMAT)
                .addEdge(NODE_FORMAT, END);

        return graph.compile();
    }
}
