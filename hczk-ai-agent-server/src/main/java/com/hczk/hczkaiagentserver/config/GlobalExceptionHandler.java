package com.hczk.hczkaiagentserver.config;

import com.hczk.hczkaiagentserver.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获 Controller 层抛出的异常，转换为标准 Result 响应格式
 * 避免将原始异常堆栈直接暴露给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理运行时异常
     * 记录完整异常堆栈（便于排查问题），将异常消息返回给前端
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);
        return Result.error(e.getMessage());
    }

    /**
     * 处理其他未捕获异常
     * 记录完整异常堆栈，向前端返回通用错误提示（不暴露内部细节）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        return Result.error("系统错误，请稍后重试");
    }
}
