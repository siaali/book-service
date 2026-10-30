package com.siaali.book.common;

/**
 * 借阅状态常量。
 *
 * 为什么不直接在代码里写 0、1、2：
 *   这叫「魔法数字（magic number）」，读代码的人看到 status == 1 不知道是借出还是归还，
 *   而且一旦数据库里的约定变了（比如 0 改成 10），要全项目搜索替换，必然漏。
 *
 * 为什么用 final class + 私有构造，而不是 interface 或 enum：
 *   - 私有构造：禁止别人 new 这个类，它只用来装常量
 *   - 不用 interface：interface 里的字段隐式 public static final，但它本意是定义行为契约，
 *     拿来装常量属于语义误用
 *   - 不用 enum：MyBatis-Plus 存 enum 需要额外配置转换器，这一版先用最直白的方式，
 *     等你能讲清楚 enum 的好处再换
 */
public final class BorrowStatus {

    /** 借出中：还没还 */
    public static final int BORROWED = 0;

    /** 已归还 */
    public static final int RETURNED = 1;

    /** 逾期：超过应还时间仍未归还，由定时任务标记 */
    public static final int OVERDUE = 2;

    /** 借阅天数：借出后 30 天内归还 */
    public static final int BORROW_DAYS = 30;

    private BorrowStatus() {
    }
}
