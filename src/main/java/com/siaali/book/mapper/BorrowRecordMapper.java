package com.siaali.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.siaali.book.entity.BorrowRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 借阅记录数据访问层。
 *
 * 这里只写两个 BaseMapper 给不了的方法：
 *   1. 幂等归还（重复调用不会重复加库存）
 *   2. 查询用户是否已借同一本书未还（用于拦截重复借阅）
 */
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord> {

    /**
     * 归还：把记录状态从「未归还」改为「已归还」，并写入归还时间。
     *
     * 【为什么 WHERE 里要带状态条件】
     *   这就是「幂等（idempotent）」的实现方式。
     *   幂等的意思是：同一个操作执行一次和执行十次，结果一样。
     *
     *   假设用户手抖点了两次"还书"，或者网络超时前端重试了一次：
     *     - 第 1 次：status 是 0 或 2 → 改成 1，影响 1 行，库存 +1
     *     - 第 2 次：status 已经是 1，WHERE 不匹配，影响 0 行，库存不动
     *
     *   如果不带这个条件，第 2 次会把已经还过的记录再"还"一次，库存被刷高，
     *   结果就是「书架上明明只有 5 本，系统显示 6 本」。
     *
     * 【为什么是 status IN (0, 2)，不是 status = 0】
     *   这是加逾期状态时踩的第三个坑，也是最隐蔽的一个 ——
     *   因为前两个坑在 Java 代码里，肉眼能看到 if，而这个坑藏在 SQL 字符串里。
     *
     *   逾期记录 status = 2，如果这里写 status = 0：
     *     应用层放行了（第 2 处的修改），SQL 这里却 WHERE 不匹配，
     *     影响 0 行，代码抛 409「归还失败，该记录状态已变更」。
     *     用户看到的是"刷新重试"，重试一百次也一样，书永远还不掉。
     *
     *   IN (0, 2) 的意思是：借出中 和 逾期 都允许归还。
     *   注意它和"已归还(1)"的边界 —— 1 不在列表里，所以重复归还依然被拦住，
     *   幂等性没有丢。
     *
     * 【为什么不用「先 select 查状态，再 update」】
     *   查和改之间有时间间隙（哪怕只有几毫秒），两个请求可能同时查到 status=0，
     *   都认为"还没还"，然后都去加库存。这叫「检查后使用（Check-Then-Act）」竞态。
     *   把判断条件写进 UPDATE 的 WHERE，让数据库在一条语句里原子完成判断和修改。
     *
     * @return 影响行数。1 = 归还成功；0 = 记录不存在 / 不是你的记录 / 已经还过了
     */
    @Update("UPDATE borrow_record SET status = 1, return_time = #{returnTime} " +
            "WHERE id = #{recordId} AND user_id = #{userId} AND status IN (0, 2)")
    int markReturned(@Param("recordId") Long recordId,
                     @Param("userId") Long userId,
                     @Param("returnTime") LocalDateTime returnTime);
    /**
     * 把「已过应还时间但仍未归还」的记录标记为逾期（status 0 → 2）。
     */
    @Update("UPDATE borrow_record SET status = 2 WHERE status = 0 AND due_time < #{now}")
    int markOverdue(@Param("now") LocalDateTime now);
    /**
     * 统计某用户对某本书「当前未归还」的借阅数量。
     *
     * 用途：拦截"同一个人重复借同一本书"（业务上不合理，一个人不需要借两本一样的书）。
     *
     * 【同样是 status IN (0, 2)，不是 status = 0】
     *   逾期(2) 也是"还没还"的一种。如果这里只写 status = 0，
     *   用户逾期之后，countActiveBorrow 返回 0，系统认为他没借过这本书，
     *   于是允许他再借一本 —— 结果一个人手上拿着两本同一本书，
     *   其中一本永远逾期，另一本正常计时。这是很典型的"新增状态漏改判断"。
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
    @Select("SELECT COUNT(*) FROM borrow_record WHERE user_id = #{userId} AND book_id = #{bookId} AND status IN (0, 2)")
    int countActiveBorrow(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 深分页：跳过 offset 条，取 size 条（LIMIT offset, size）。
     *
     * 【为什么慢】
     * MySQL 拿到 LIMIT 99990, 10 之后的执行方式不是"跳到第 99991 条"，
     * 而是"从头读，读一条丢一条，数到 99990 之后才开始返回"。
     * offset 越大，白读的行越多，代价线性增长。
     *
     * 【为什么用 @Select 手写 SQL，不用 MyBatis-Plus 的 selectPage】
     * 因为这次实验的目的就是观察 LIMIT offset, size 这个具体写法的性能。
     * 用框架包一层，就看不清楚它到底生成了什么 SQL。
     */
    @Select("SELECT * FROM borrow_record ORDER BY id DESC LIMIT #{offset}, #{size}")
    List<BorrowRecord> pageByOffset(@Param("offset") long offset, @Param("size") int size);

    /**
     * 游标分页：只取 id 比 lastId 更小的记录（WHERE id < lastId）。
     *
     * 【和上面那条的本质区别】
     *   offset 方式告诉数据库"跳过多少条" → 数据库必须真的数过去
     *   游标方式告诉数据库"从哪个位置开始" → 数据库用 B+ 树直接定位
     *   前者代价随页码增长，后者恒定。
     *
     * 【代价】
     *   只能"下一页"，不能跳到第 N 页；
     *   要求排序字段唯一且有序（这里用主键 id，天然满足）。
     *   所以它适合信息流、无限下拉，不适合带页码导航的后台列表。
     */
    @Select("SELECT * FROM borrow_record WHERE id < #{lastId} ORDER BY id DESC LIMIT #{size}")
    List<BorrowRecord> pageByCursor(@Param("lastId") long lastId, @Param("size") int size);
}
