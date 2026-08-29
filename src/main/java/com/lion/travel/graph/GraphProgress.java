package com.lion.travel.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph 工作流执行进度记录器(内存版)。
 *
 * <p>供前端流程图实时展示各节点执行状态:请求方生成 {@code requestId} 并随
 * Graph 输入传入,节点执行时通过 {@link #mark} 打点,前端轮询 {@link #get} 获取。
 * 数据仅保存在内存,任务完成后由后端保留在 {@link #MAX_TASKS} 上限内自动淘汰。</p>
 *
 * <p>节点状态取值:{@link #PENDING} 等待 / {@link #RUNNING} 执行中 / {@link #DONE} 完成。</p>
 */
@Slf4j
@Component
public class GraphProgress {

    /** 节点状态:等待 */
    public static final String PENDING = "PENDING";
    /** 节点状态:执行中 */
    public static final String RUNNING = "RUNNING";
    /** 节点状态:完成 */
    public static final String DONE = "DONE";

    /** 内存中最多保留的任务数,防止进度数据无限增长 */
    private static final int MAX_TASKS = 20;

    /** requestId -> 节点状态(LinkedHashMap 保持打点顺序,即执行顺序) */
    private final Map<String, Map<String, NodeState>> progresses = new ConcurrentHashMap<>();

    /**
     * 初始化一个任务(打点容器),指定 requestId。
     */
    public void begin(String requestId) {
        progresses.put(requestId, new LinkedHashMap<>());
        trim();
    }

    /**
     * 初始化一个任务并自动生成 requestId(供 MCP 工具等不关心进度的调用方使用)。
     *
     * @return 生成的 requestId
     */
    public String begin() {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        begin(requestId);
        return requestId;
    }

    /**
     * 节点打点:记录某个节点的当前状态。
     *
     * @param requestId 任务 id
     * @param node      节点英文名(与 Graph 节点一致)
     * @param label     节点中文名(前端展示用)
     * @param status    状态(PENDING/RUNNING/DONE)
     */
    public void mark(String requestId, String node, String label, String status) {
        Map<String, NodeState> map = progresses.get(requestId);
        if (map == null) {
            return;
        }
        map.put(node, new NodeState(node, label, status));
    }

    /**
     * 结束任务:将尚未标记完成的节点统一置为 DONE。
     */
    public void finish(String requestId) {
        Map<String, NodeState> map = progresses.get(requestId);
        if (map == null) {
            return;
        }
        map.values().forEach(state -> state.setStatus(DONE));
    }

    /**
     * 查询任务节点状态列表(按执行顺序)。
     */
    public List<NodeState> get(String requestId) {
        Map<String, NodeState> map = progresses.getOrDefault(requestId, Map.of());
        return new ArrayList<>(map.values());
    }

    /**
     * 淘汰最旧的任务,保证内存上限。
     */
    private void trim() {
        if (progresses.size() > MAX_TASKS) {
            progresses.keySet().stream().findFirst().ifPresent(progresses::remove);
        }
    }

    /**
     * 节点状态快照。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeState {
        /** 节点英文名 */
        private String node;
        /** 节点中文名 */
        private String label;
        /** 执行状态:PENDING / RUNNING / DONE */
        private String status;
    }
}
