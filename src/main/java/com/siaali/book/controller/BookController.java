package com.siaali.book.controller;

import com.siaali.book.common.Result;
import com.siaali.book.dto.BookCreateDTO;
import com.siaali.book.dto.PageResult;
import com.siaali.book.entity.Book;
import com.siaali.book.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /**
     * 分页查询。
     * GET /api/books?page=1&size=10&keyword=Java
     */
    @GetMapping
    public Result<PageResult<Book>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(bookService.page(page, size, keyword));
    }

    /**
     * 查详情。
     * GET /api/books/1
     */
    @GetMapping("/{id}")
    public Result<Book> detail(@PathVariable Long id) {
        return Result.ok(bookService.getById(id));
    }

    /**
     * 新增。
     * POST /api/books
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody BookCreateDTO dto) {
        return Result.ok(bookService.create(dto));
    }

    /** 连通性自检，不依赖数据库 */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }
}
