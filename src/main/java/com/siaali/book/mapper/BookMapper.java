package com.siaali.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.siaali.book.entity.Book;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 图书数据访问层。
 *
 * 继承 BaseMapper 拿到单表 CRUD 能力（等价于课程实验里手写的 BookDAO + BookDAOImpl），
 * 复杂的、需要精确控制 SQL 的场景再手写方法 —— 比如下面的扣库存。
 */
public interface BookMapper extends BaseMapper<Book> {

    /**
     * 扣减可借库存（CAS：判断与扣减在同一条 SQL 里完成）。
     *
     * 为什么不用「先查库存再更新」：
     *   查和改之间有间隙，两个线程可能都查到 1，都判断"够扣"，最后扣成 -1（超卖）。
     * 为什么不用 version：
     *   条件 available_stock > 0 本身就是原子判断，已经保证了不会扣成负数，
     *   再叠一个 version 属于重复保护，只会多一次失败重试。
     *
     * @return 影响行数。1 = 扣减成功；0 = 库存不足或图书不存在
     */
    @Update("UPDATE book SET available_stock = available_stock - 1, version = version + 1 " +
            "WHERE id = #{bookId} AND available_stock > 0")
    int deductStock(@Param("bookId") Long bookId);

    /**
     * 归还：可借库存 +1，但不能超过总库存（防止重复归还把库存刷高）。
     */
    @Update("UPDATE book SET available_stock = available_stock + 1, version = version + 1 " +
            "WHERE id = #{bookId} AND available_stock < total_stock")
    int increaseStock(@Param("bookId") Long bookId);
}
