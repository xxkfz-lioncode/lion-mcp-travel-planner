package com.lion.travel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成旅行计划请求参数。
 */
@Data
public class GeneratePlanRequest {

    /** 用户旅行需求,如 "北京3天亲子游,预算5000元" */
    @NotBlank(message = "旅行需求不能为空")
    private String requirements;

    /** 前端生成的请求 id(用于轮询 Graph 执行进度,可选) */
    private String requestId;
}
