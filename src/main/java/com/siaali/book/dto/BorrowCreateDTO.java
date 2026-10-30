package com.siaali.book.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 借书请求参数。
 *
 * 只允许客户端传这两个字段，其余（借出时间、应还时间、状态）全部由服务端计算。
 * 这是「永远不要相信客户端」原则的具体体现：
 * 如果让客户端传 borrowTime，它可以传一个 1970 年的时间来伪造借阅历史。
 *
 * 关于注解（jakarta.validation 包，以前叫 javax.validation）：
 *   @NotNull  —— 不能为 null（但可以是 0，所以还要配 @Positive）
 *   @Positive —— 必须是正数（ID 不能是 0 或负数）
 * 这些注解本身不生效，需要 Controller 参数上加 @Valid 触发校验。
 */
public record BorrowCreateDTO(

        @NotNull(message = "图书ID不能为空")
        @Positive(message = "图书ID必须为正数")
        Long bookId,

        @NotNull(message = "用户ID不能为空")
        @Positive(message = "用户ID必须为正数")
        Long userId
) {
}
