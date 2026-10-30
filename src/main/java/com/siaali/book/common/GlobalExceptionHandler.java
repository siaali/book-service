package com.siaali.book.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 *
 * 解决的问题：不加这个，异常会以 Spring 默认的错误页/JSON 返回，
 * 前端拿到的结构和你正常返回的 Result 完全不一样，没法统一处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，用 warn 级别，不打堆栈 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：把第一个字段错误返回，别把整串堆栈丢给前端 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "参数校验失败"
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return Result.fail(400, msg);
    }

    /** 兜底：未预期的异常要打完整堆栈，否则线上没法排查 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(500, "服务器内部错误");
    }
}
