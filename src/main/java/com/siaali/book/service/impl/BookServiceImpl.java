package com.siaali.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.siaali.book.common.BizException;
import com.siaali.book.dto.BookCreateDTO;
import com.siaali.book.dto.PageResult;
import com.siaali.book.entity.Book;
import com.siaali.book.mapper.BookMapper;
import com.siaali.book.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;

    @Override
    public PageResult<Book> page(int page, int size, String keyword) {
        Page<Book> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 用 and(...) 包一层，否则 or 会和后面可能追加的条件串错
            wrapper.and(w -> w.like(Book::getName, keyword)
                    .or()
                    .like(Book::getAuthor, keyword));
        }
        wrapper.orderByAsc(Book::getId);

        Page<Book> result = bookMapper.selectPage(pageParam, wrapper);

        List<Book> records = result.getRecords();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public Book getById(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BizException(404, "图书不存在: " + id);
        }
        return book;
    }

    @Override
    public Long create(BookCreateDTO dto) {
        Book book = new Book();
        book.setIsbn(dto.getIsbn());
        book.setName(dto.getName());
        book.setAuthor(dto.getAuthor());
        book.setPublisher(dto.getPublisher());
        book.setPrice(dto.getPrice());
        book.setType(dto.getType());
        book.setTotalStock(dto.getTotalStock());
        // 新书全部可借
        book.setAvailableStock(dto.getTotalStock());
        book.setVersion(0);

        bookMapper.insert(book);
        return book.getId();
    }
}
