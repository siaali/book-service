package com.siaali.book;

import com.siaali.book.dto.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 不依赖 Spring 容器和数据库的纯单元测试。
 *
 * 为什么先写这种：Spring Boot 的 @SpringBootTest 需要真实数据库，
 * 环境没准备好时会让整个测试阶段红掉，反而掩盖真正的问题。
 */
class PageResultTest {

    @Test
    @DisplayName("总数为 20、每页 10 条时，应该算出 2 页")
    void shouldCalculatePages() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 20L, 1L, 10L);
        assertEquals(2L, result.pages());
    }

    @Test
    @DisplayName("总数不能整除时要向上取整，21 条每页 10 条应该是 3 页")
    void shouldRoundUp() {
        PageResult<String> result = PageResult.of(List.of(), 21L, 1L, 10L);
        assertEquals(3L, result.pages());
    }

    @Test
    @DisplayName("总数为 0 时页数为 0，不能抛异常")
    void shouldHandleEmpty() {
        PageResult<String> result = PageResult.of(List.of(), 0L, 1L, 10L);
        assertEquals(0L, result.pages());
    }
}
