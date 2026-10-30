package com.siaali.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.siaali.book.common.BizException;
import com.siaali.book.common.BorrowStatus;
import com.siaali.book.dto.BorrowCreateDTO;
import com.siaali.book.dto.ReturnDTO;
import com.siaali.book.entity.Book;
import com.siaali.book.entity.BorrowRecord;
import com.siaali.book.mapper.BookMapper;
import com.siaali.book.mapper.BorrowRecordMapper;
import com.siaali.book.service.BorrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅业务实现。
 *
 * ══════════════════════════════════════════════════════════════
 * 核心知识点：@Transactional 的四个失效场景（面试高频，也是线上真实事故来源）
 * ══════════════════════════════════════════════════════════════
 *
 * 1. 加在 private / final 方法上 → 完全无效
 *    Spring 的事务靠「动态代理」实现：Spring 不直接调用你的方法，而是在外面包一层代理对象，
 *    由代理在调用前开启事务、调用后提交。Java 的代理机制无法拦截 private 方法，
 *    所以加在 private 方法上的 @Transactional 会被静默忽略，不报错，但事务不存在。
 *
 * 2. 同类内部方法互相调用 → 无效
 *    下面如果写成 this.someTransactionalMethod()，调用的是对象自己的方法，
 *    绕过了代理对象，事务不生效。必须通过代理对象调用（注入自己，或用 AopContext）。
 *
 * 3. 异常被 try-catch 吞掉 → 不回滚
 *    事务是否回滚，取决于异常是否"抛出到代理层"。如果方法内部 catch 了异常没有重新抛出，
 *    代理认为方法正常结束，会照常提交。所以"记录日志然后继续"这种写法必须配合手动回滚。
 *
 * 4. 默认只回滚 RuntimeException 和 Error，不回滚受检异常 → 必须写 rollbackFor
 *    这是最容易被忽略的一条。Java 的异常分两类：
 *      - 非受检异常（RuntimeException 及其子类）：编译器不强制处理，如 NullPointerException
 *      - 受检异常（Exception 下除 RuntimeException 外的）：编译器强制 try-catch 或 throws，
 *        如 IOException、SQLException
 *    Spring 的默认规则是"只对非受检异常回滚"，因为受检异常通常代表"可预期的业务分支"。
 *    但实际项目中，文件读写失败、数据库连接中断都可能抛受检异常，此时事务会提交一半的数据。
 *    所以统一写 rollbackFor = Exception.class，明确表示"任何异常都回滚"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowServiceImpl implements BorrowService {

    private final BookMapper bookMapper;
    private final BorrowRecordMapper borrowRecordMapper;

    /**
     * 借书。
     *
     * 执行顺序（顺序不是随便定的，每一步的理由都写在注释里）：
     *   ① 查图书是否存在  → 给出明确错误信息，而不是让用户看到"库存不足"
     *   ② 查是否重复借阅  → 业务规则拦截
     *   ③ CAS 扣库存      → 真正的并发控制点
     *   ④ 插入借阅记录    → 事务的最后一步
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long borrow(BorrowCreateDTO dto) {
        Long bookId = dto.bookId();
        Long userId = dto.userId();

        // ① 校验图书存在。
        //    为什么这步在事务内、在扣库存之前：
        //    如果先扣库存，扣完才发现书不存在（影响行数 0），我们无法区分
        //    "书不存在"和"库存不足"，只能给用户一个含糊的"借书失败"。
        //    先查一次，能给出准确原因。这一次查询的代价（走主键索引，微秒级）远小于体验损失。
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BizException(404, "图书不存在: " + bookId);
        }

        // ② 拦截重复借阅。
        //    注意：这个校验不是原子的，并发下可能漏过（详见 BorrowRecordMapper 的注释）。
        //    这里保留它，是因为它能覆盖绝大多数真实场景，且成本极低（走 idx_user_status 索引）。
        if (borrowRecordMapper.countActiveBorrow(userId, bookId) > 0) {
            throw new BizException(400, "你已借阅《" + book.getName() + "》且尚未归还");
        }

        // ③ 扣库存 —— 整个借书流程唯一真正的并发控制点。
        //    为什么用"影响行数"判断成功与否，而不是先 select 出 availableStock 再比较：
        //      先查后改是「检查后使用（Check-Then-Act）」竞态。
        //      库存 1 本，两个线程同时查到 availableStock = 1，都判断"够借"，
        //      然后都执行 available_stock - 1，结果变成 -1，超卖。
        //    deductStock 的 SQL 是 UPDATE ... WHERE available_stock > 0，
        //    数据库在执行时会对该行加「排他锁」，两个 UPDATE 自动排队，
        //    第二个执行时 available_stock 已经是 0，WHERE 不匹配，影响 0 行。
        //    这个模式叫 CAS（Compare-And-Swap，比较并交换），数据库层面就是"条件更新"。
        int deducted = bookMapper.deductStock(bookId);
        if (deducted == 0) {
            // 走到这里说明：前面查到书存在，但扣减时 WHERE 条件不满足。
            // 唯一可能就是 available_stock 已经 <= 0，即库存不足。
            throw new BizException(400, "《" + book.getName() + "》库存不足，暂不可借");
        }

        // ④ 插入借阅记录。
        //    这一步如果失败（比如数据库连接断了），抛出的异常会让整个事务回滚，
        //    第 ③ 步的库存扣减也会被撤销 —— 这就是原子性。
        LocalDateTime now = LocalDateTime.now();
        BorrowRecord record = new BorrowRecord();
        record.setBookId(bookId);
        record.setUserId(userId);
        record.setBorrowTime(now);
        record.setDueTime(now.plusDays(BorrowStatus.BORROW_DAYS));
        record.setStatus(BorrowStatus.BORROWED);
        // returnTime 保持 null，表示还没还

        borrowRecordMapper.insert(record);

        log.info("借书成功 bookId={} userId={} recordId={}", bookId, userId, record.getId());
        return record.getId();
    }

    /**
     * 还书。
     *
     * 执行顺序：
     *   ① 查记录并校验归属、状态 → 给出准确错误信息
     *   ② 幂等更新记录状态       → 用 WHERE status = 0 兜底
     *   ③ 库存 +1                → 用 WHERE available_stock < total_stock 兜底
     *
     * 为什么 ① 的校验不能代替 ② 的条件：
     *   ① 是应用层判断，两个并发请求可能都通过；② 是数据库层判断，一定只有一个成功。
     *   应用层校验负责"体验"，数据库条件负责"正确"。两者都要有。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnBook(ReturnDTO dto) {
        Long recordId = dto.recordId();
        Long userId = dto.userId();

        // ① 查记录。不存在、不是本人的、已还过，分别给出不同提示
        BorrowRecord record = borrowRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(404, "借阅记录不存在: " + recordId);
        }
        // 用 equals 比较 Long 而不是 ==。
        // 原因：Long 是包装类型，== 比较的是对象引用。
        // 当数值超出 -128 ~ 127 时，Long 不再走缓存，两个值相等的 Long 对象引用也不同，
        // == 会返回 false。这是 Java 最经典的陷阱之一（Integer 同理）。
        if (!record.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作他人的借阅记录");
        }
        if (record.getStatus() != BorrowStatus.BORROWED) {
            throw new BizException(400, "该借阅记录已归还，请勿重复操作");
        }

        // ② 幂等更新。影响 0 行说明并发下已被其他请求抢先归还
        int updated = borrowRecordMapper.markReturned(recordId, userId, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(409, "归还失败，该记录状态已变更，请刷新后重试");
        }

        // ③ 库存 +1。
        //    WHERE available_stock < total_stock 是防止库存被刷高的最后一道防线：
        //    如果因为历史脏数据导致 available_stock 已经等于 total_stock，
        //    这里影响 0 行，事务回滚，记录状态改回"借出中"，不会产生不一致。
        int increased = bookMapper.increaseStock(record.getBookId());
        if (increased == 0) {
            throw new BizException(500, "库存数据异常，归还失败，请联系管理员");
        }

        log.info("还书成功 recordId={} userId={} bookId={}", recordId, userId, record.getBookId());
    }

    @Override
    public List<BorrowRecord> listActiveByUser(Long userId) {
        LambdaQueryWrapper<BorrowRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BorrowRecord::getUserId, userId)
                .eq(BorrowRecord::getStatus, BorrowStatus.BORROWED)
                .orderByDesc(BorrowRecord::getBorrowTime);
        return borrowRecordMapper.selectList(wrapper);
    }
}
