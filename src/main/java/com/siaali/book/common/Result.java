package com.siaali.book.common;

/**
 * 统一返回体。
 *
 * 用 record 而不是普通类：字段不可变、自动生成 getter/equals/hashCode，
 * 这类纯数据载体用 record 比 Lombok @Data 更合适。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(200, "ok", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
