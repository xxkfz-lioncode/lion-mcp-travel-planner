package com.lion.travel.controller;

import com.lion.travel.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
/**
 * 大模型连通性测试接口。
 *
 * <p>用于快速验证 DashScope(通义千问)对接是否可用,方便排查
 * 400/404/超时等配置问题。Spring MVC 同步调用 {@link ChatModel#call}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class AiTestController {

    private final ChatModel chatModel;

    /**
     * 测试大模型:GET /api/test/ai?prompt=你好
     */
    @GetMapping("/ai")
    public Result<String> testAi(@RequestParam(required = false, defaultValue = "你好,请用一句话自我介绍") String prompt) {
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String text = response.getResult().getOutput().getText();
            long cost = System.currentTimeMillis() - start;
            log.info("大模型测试成功,耗时 {}ms,回复:{}", cost, text);
            return Result.ok("模型响应(" + cost + "ms): " + text);
        } catch (Exception e) {
            log.error("大模型测试失败", e);
            return Result.<String>fail("大模型调用失败: " + e.getMessage());
        }
    }
}
