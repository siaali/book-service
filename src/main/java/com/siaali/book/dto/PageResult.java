package com.siaali.book.dto;

import java.util.List;

/**
 * 分页返回体。
 *
 * 不直接把 MyBatis-Plus 的 Page 对象返回给前端，原因：
 * Page 里有一堆内部字段（optimizeCountSql、searchCount 等），
 * 暴露出去既冗余，又让前端耦合了 ORM 的实现细节。
 */
public record PageResult<T>(List<T> records, long total, long current, long size, long pages) {

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        long pages = size == 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(records, total, current, size, pages);
    }
}
