package com.siaali.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 借阅记录实体，对应 borrow_record 表。
 *
 * 一条记录的生命周期：
 *   借出（status=0） → 归还（status=1）
 *                    ↘ 超期未还（status=2，由定时任务扫描 due_time 得出）
 *
 * 为什么要把「借阅」单独建一张表，而不是在 book 表上加个「借阅人」字段：
 *   一本书可以被多个人先后借阅，一个字段只能存一个人，会覆盖历史。
 *   而且借阅历史本身是有价值的业务数据（谁在什么时候借过什么）。
 *   这是数据库设计的第一范式要求：一个字段只存一个原子值，不能存"多人列表"。
 */
@Data
@TableName("borrow_record")
public class BorrowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图书 ID，逻辑上关联 book.id。注意：这里不建外键，原因见下方说明 */
    private Long bookId;

    /** 借阅人 ID。本版本没有登录功能，由接口参数传入，写死 1 也能跑 */
    private Long userId;

    /** 借出时间 */
    private LocalDateTime borrowTime;

    /** 应还时间 = 借出时间 + 借阅天数 */
    private LocalDateTime dueTime;

    /** 实际归还时间。未归还时为 null，这个 null 本身就是"还没还"的语义 */
    private LocalDateTime returnTime;

    /**
     * 状态：0 借出中 / 1 已归还 / 2 逾期
     *
     * 用 int 而不是 boolean，是因为状态未来会扩展（比如 3=丢失、4=续借）。
     * boolean 只有两个值，扩展时改表结构代价很大。
     *
     * 用 int 而不是枚举（enum），是为了让 MyBatis-Plus 不需要额外配置枚举转换器。
     * 生产项目里更推荐枚举 + @EnumValue，类型安全、不会传错值。
     */
    private Integer status;
}
