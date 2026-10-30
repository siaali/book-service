package com.siaali.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.siaali.book.entity.BorrowRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 借阅记录数据访问层。
 *
 * 这里只写两个 BaseMapper 给不了的方法：
 *   1. 幂等归还（重复调用不会重复加库存）
 *   2. 查询用户是否已借同一本书未还（用于拦截重复借阅）
 */
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord> {

    /**
     * 归还：把记录状态从「借出中」改为「已归还」，并写入归还时间。
     *
     * 【为什么 WHERE 里要带 status = 0】
     *   这就是「幂等（idempotent）」的实现方式。
     *   幂等的意思是：同一个操作执行一次和执行十次，结果一样。
     *
     *   假设用户手抖点了两次"还书"，或者网络超时前端重试了一次：
     *     - 第 1 次：status=0 → 改成 1，影响 1 行，库存 +1
     *     - 第 2 次：status 已经是 1，WHERE 不匹配，影响 0 行，库存不动
     *
     *   如果不带这个条件，第 2 次会把已经还过的记录再"还"一次，库存被刷高，
     *   结果就是「书架上明明只有 5 本，系统显示 6 本」。
     *
     * 【为什么不用「先 select 查状态，再 update」】
     *   查和改之间有时间间隙（哪怕只有几毫秒），两个请求可能同时查到 status=0，
     *   都认为"还没还"，然后都去加库存。这叫「检查后使用（Check-Then-Act）」竞态。
     *   把判断条件写进 UPDATE 的 WHERE，让数据库在一条语句里原子完成判断和修改。
     *
     * @return 影响行数。1 = 归还成功；0 = 记录不存在 / 不是你的记录 / 已经还过了
     */
    @Update("UPDATE borrow_record SET status = 1, return_time = #{returnTime} " +
            "WHERE id = #{recordId} AND user_id = #{userId} AND status = 0")
    int markReturned(@Param("recordId") Long recordId,
                     @Param("userId") Long userId,
                     @Param("returnTime") LocalDateTime returnTime);

    /**
     * 统计某用户对某本书「当前未归还」的借阅数量。
     *
     * 用途：拦截"同一个人重复借同一本书"（业务上不合理，一个人不需要借两本一样的书）。
     *
     * 【这个方法是「非原子」的，有并发漏洞 —— 必须知道这一点】
     *   两个请求同时进来，都查到这个 count = 0，都判断"没借过"，都插入成功，
     *   结果同一个人对同一本书产生了 2 条借出记录。
     *
     *   这不是写法问题，是「先查后写」这个模式本身的缺陷，加锁或加唯一索引才能根治。
     *   生产环境的做法是在数据库上加唯一约束兜底（用户ID + 图书ID + 有效标记），
     *   应用层的校验只用来给用户友好提示，不能当成最后一道防线。
     *
     *   本版本保留这个应用层校验，是因为它能覆盖 99% 的真实场景（用户不会恶意并发），
     *   而数据库唯一约束需要额外设计字段，留给后续迭代。
     */
    @Select("SELECT COUNT(*) FROM borrow_record WHERE user_id = #{userId} AND book_id = #{bookId} AND status = 0")
    int countActiveBorrow(@Param("userId") Long userId, @Param("bookId") Long bookId);
}
