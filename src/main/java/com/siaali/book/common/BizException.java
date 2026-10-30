package com.siaali.book.common;

/**
 * 业务异常。
 *
 * 继承 RuntimeException（非受检），原因：
 * 1. Service 层抛出后能被 @Transactional 默认回滚
 * 2. 不用在方法签名上层层 throws，避免污染接口
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
