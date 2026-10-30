package com.siaali.book.service;

import com.siaali.book.dto.BookCreateDTO;
import com.siaali.book.dto.PageResult;
import com.siaali.book.entity.Book;

public interface BookService {

    /** 分页查询，keyword 对书名和作者做模糊匹配 */
    PageResult<Book> page(int page, int size, String keyword);

    /** 查详情，不存在抛 BizException */
    Book getById(Long id);

    /** 新增图书，返回自增主键 */
    Long create(BookCreateDTO dto);
}
