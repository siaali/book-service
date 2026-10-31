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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    /** 缓存 key 前缀。加前缀是为了和其他业务的 key 区分，也方便用 KEYS book:* 排查 */
    private static final String CACHE_KEY_PREFIX = "book:detail:";

    /** 正常数据的缓存时间：30 分钟 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /** 空值标记的缓存时间：2 分钟（比正常数据短得多，理由见注释） */
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    /** 空值哨兵：用它表示"这个 id 查过，数据库里确实没有" */
    private static final String EMPTY_MARKER = "__EMPTY__";

    private final BookMapper bookMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public PageResult<Book> page(int page, int size, String keyword) {
        Page<Book> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
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
        String key = CACHE_KEY_PREFIX + id;

        // ① 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (EMPTY_MARKER.equals(cached)) {
                // 命中的是空值哨兵 → 说明之前查过，数据库里没有这本书
                throw new BizException(404, "图书不存在: " + id);
            }
            log.debug("缓存命中 key={}", key);
            return (Book) cached;
        }

        // ② 未命中，查数据库
        log.debug("缓存未命中 key={}", key);
        Book book = bookMapper.selectById(id);

        // ③ 数据库也没有 → 把"空"也缓存起来（防缓存穿透）
        if (book == null) {
            redisTemplate.opsForValue().set(key, EMPTY_MARKER, withJitter(NULL_TTL));
            throw new BizException(404, "图书不存在: " + id);
        }

        // ④ 写回缓存，TTL 加随机抖动（防缓存雪崩）
        redisTemplate.opsForValue().set(key, book, withJitter(CACHE_TTL));
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
        book.setAvailableStock(dto.getTotalStock());
        book.setVersion(0);

        bookMapper.insert(book);
        return book.getId();
    }

    /** 在基础 TTL 上叠加 0~10% 的随机偏移 */
    private Duration withJitter(Duration base) {
        long seconds = base.toSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(seconds / 10 + 1);
        return Duration.ofSeconds(seconds + jitter);
    }
}