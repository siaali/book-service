package com.siaali.book.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 还书请求参数。
 *
 * 为什么用 recordId 而不是 bookId：
 *   同一个人可能借过同一本书多次（先借后还，再借），用 bookId 无法确定要还哪一条记录。
 *   recordId 是借阅记录的唯一标识，精确无歧义。
 *
 * 为什么还要带 userId：
 *   防止 A 用户拿到 B 用户的 recordId 后替别人还书。
 *   SQL 里 WHERE 会同时校验 id 和 user_id，只带 id 是越权漏洞。
 *   （专业说法叫「水平越权」：同一权限级别的用户访问了别人的数据）
 */
public record ReturnDTO(

        @NotNull(message = "借阅记录ID不能为空")
        @Positive(message = "借阅记录ID必须为正数")
        Long recordId,

        @NotNull(message = "用户ID不能为空")
        @Positive(message = "用户ID必须为正数")
        Long userId
) {
}
