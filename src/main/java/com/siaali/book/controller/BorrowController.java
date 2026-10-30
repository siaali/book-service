package com.siaali.book.controller;

import com.siaali.book.common.Result;
import com.siaali.book.dto.BorrowCreateDTO;
import com.siaali.book.dto.ReturnDTO;
import com.siaali.book.entity.BorrowRecord;
import com.siaali.book.service.BorrowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 借阅接口。
 *
 * Controller 的职责只有三件事：接参数 → 调 Service → 包装返回值。
 * 一旦 Controller 里出现业务判断（if 库存不足、if 用户黑名单），就是分层错了，
 * 因为这段逻辑无法被其他入口（定时任务、消息队列消费者）复用，也无法单独测试。
 *
 * 【关于 HTTP 状态码和业务码的区别 —— 面试常问】
 * 本项目的风格是：HTTP 状态码永远是 200（除了参数校验失败返回 400），
 * 真正的成功/失败放在响应体的 code 字段里。
 *
 * 这样做的好处：前端只需判断 body.code，不用同时处理 HTTP 层和业务层两套错误。
 * 缺点是：不符合 REST 的严格语义（资源不存在应该返回 404 状态码）。
 *
 * 两种做法都对，关键是团队内统一。真实项目里，面向 C 端 App 的接口多用"HTTP 200 + 业务码"，
 * 面向内部服务的接口多用"HTTP 状态码 + Problem Details 标准"。
 */
@RestController
@RequestMapping("/api/borrows")
@RequiredArgsConstructor
public class BorrowController {

    private final BorrowService borrowService;

    /**
     * 借书。
     * POST /api/borrows
     * Body: {"bookId": 1, "userId": 1}
     *
     * @Valid 的作用：触发 DTO 上的 @NotNull / @Positive 校验。
     * 不写这个注解，DTO 里的校验注解完全不生效，非法参数会一路传到 Service。
     */
    @PostMapping
    public Result<Long> borrow(@Valid @RequestBody BorrowCreateDTO dto) {
        return Result.ok(borrowService.borrow(dto));
    }

    /**
     * 还书。
     * POST /api/borrows/return
     * Body: {"recordId": 1, "userId": 1}
     *
     * 为什么还书用 POST 而不是 PUT/PATCH：
     * "归还"是一个业务动作，不是对资源做完整替换。动作类接口用 POST 是通用做法。
     * 如果严格按 REST 设计，应该是 PATCH /api/borrows/1 {status: 1}，
     * 但那样会让客户端有能力改 status 为任意值，反而不安全。
     */
    @PostMapping("/return")
    public Result<Void> returnBook(@Valid @RequestBody ReturnDTO dto) {
        borrowService.returnBook(dto);
        return Result.ok();
    }

    /**
     * 查询某用户当前借出未还的书。
     * GET /api/borrows?userId=1
     */
    @GetMapping
    public Result<List<BorrowRecord>> listActive(@RequestParam Long userId) {
        return Result.ok(borrowService.listActiveByUser(userId));
    }
}
