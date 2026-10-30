package com.siaali.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 图书实体，对应 book 表。
 *
 * 与课程实验版本的差异：
 * 1. 主键改为自增（原版手工指定 book_id，重跑必主键冲突）
 * 2. 价格由 double 改为 BigDecimal（金额不能用浮点数）
 * 3. 新增库存字段，为「并发扣减」提供业务载体
 */
@Data
@TableName("book")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String isbn;

    private String name;

    private String author;

    private String publisher;

    /** 金额必须用 BigDecimal，double 会有精度丢失 */
    private BigDecimal price;

    private String type;

    /** 总库存 */
    private Integer totalStock;

    /** 可借库存：并发扣减就改这个字段 */
    private Integer availableStock;

    /** 乐观锁版本号，非库存扣减场景使用 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
