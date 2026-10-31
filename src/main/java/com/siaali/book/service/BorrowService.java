package com.siaali.book.service;

import com.siaali.book.dto.BorrowCreateDTO;
import com.siaali.book.dto.ReturnDTO;
import com.siaali.book.entity.BorrowRecord;

import java.util.List;

/**
 * 借阅业务接口。
 *
 * 为什么要先写接口再写实现（很多新手觉得这是多此一举）：
 *   1. Controller 依赖接口而不是实现类，将来换实现（比如加缓存的版本）不用改 Controller
 *   2. Spring 的事务、缓存等能力都是通过「动态代理」实现的，代理的是接口方法。
 *      （Spring Boot 3 之后也能代理类，但接口仍然是更清晰的做法）
 *   3. 单元测试可以给接口造一个假的实现，不用启动整个 Spring 容器
 */
public interface BorrowService {

    /**
     * 借书。
     *
     * @return 新生成的借阅记录 ID
     */
    Long borrow(BorrowCreateDTO dto);

    /**
     * 还书。
     */
    void returnBook(ReturnDTO dto);

    /**
     * 查询某用户当前借出未还的记录。
     */
    List<BorrowRecord> listActiveByUser(Long userId);

    /**
     * 深分页查询全站借阅流水（offset 方式，可以跳页）。
     *
     * @param offset 跳过多少条
     * @param size   每页多少条
     */
    List<BorrowRecord> pageByOffset(long offset, int size);

    /**
     * 游标分页查询全站借阅流水（只能翻下一页）。
     *
     * @param lastId 上一页最后一条的 id，第一页传 Long.MAX_VALUE
     * @param size   每页多少条
     */
    List<BorrowRecord> pageByCursor(long lastId, int size);
}
