package com.lion.travel.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 返回结果封装。
 *
 * <p>所有 REST 接口统一返回该结构,便于前端与调用方处理;
 * MCP 工具方法则直接返回业务数据(String / List 等)。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码:200 成功,其余为失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 是否成功 */
    public boolean isSuccess() {
        return code == 200;
    }

    /** 成功(带数据) */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 成功(不带数据) */
    public static <T> Result<T> ok() {
        return new Result<>(200, "操作成功", null);
    }

    /** 失败(自定义状态码与信息) */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败(默认 500) */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
