-- ============================================================
-- 图书借阅服务 表结构
-- 执行方式：mysql -u root -p < src/main/resources/db/schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS book_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE book_db;

DROP TABLE IF EXISTS borrow_record;
DROP TABLE IF EXISTS book;

-- ------------------------------------------------------------
-- 图书表
-- ------------------------------------------------------------
CREATE TABLE book (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    isbn            VARCHAR(20)   NOT NULL                COMMENT 'ISBN',
    name            VARCHAR(100)  NOT NULL                COMMENT '书名',
    author          VARCHAR(50)   NOT NULL                COMMENT '作者',
    publisher       VARCHAR(50)   DEFAULT NULL            COMMENT '出版社',
    -- 金额用 DECIMAL 不用 DOUBLE：double 存 0.1 会变成 0.1000000000000000055
    price           DECIMAL(10,2) NOT NULL                COMMENT '价格',
    type            VARCHAR(20)   DEFAULT NULL            COMMENT '分类',
    total_stock     INT           NOT NULL DEFAULT 0      COMMENT '总库存',
    available_stock INT           NOT NULL DEFAULT 0      COMMENT '可借库存',
    -- version 留给「读出来-改-写回」的场景（如修改图书信息），库存扣减不用它
    version         INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_isbn (isbn),
    -- 书名、作者是列表页的模糊查询字段，建索引避免全表扫描
    KEY idx_name (name),
    KEY idx_author (author)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图书';

-- ------------------------------------------------------------
-- 借阅记录表
-- ------------------------------------------------------------
CREATE TABLE borrow_record (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    book_id     BIGINT   NOT NULL                COMMENT '图书ID',
    user_id     BIGINT   NOT NULL                COMMENT '借阅人ID',
    borrow_time DATETIME NOT NULL                COMMENT '借出时间',
    due_time    DATETIME NOT NULL                COMMENT '应还时间',
    return_time DATETIME DEFAULT NULL            COMMENT '实际归还时间',
    status      TINYINT  NOT NULL DEFAULT 0      COMMENT '0借出中 1已归还 2逾期',
    PRIMARY KEY (id),
    -- 查「某人当前借了哪些书」走这个索引
    KEY idx_user_status (user_id, status),
    -- 查「某本书被谁借着」走这个索引
    KEY idx_book_status (book_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '借阅记录';

-- ------------------------------------------------------------
-- 测试数据：20 本，够验证分页（每页 10 条 = 2 页）
-- ------------------------------------------------------------
INSERT INTO book (isbn, name, author, publisher, price, type, total_stock, available_stock, version) VALUES
('9787111000001', 'Java 编程思想',        'Bruce Eckel',      '机械工业出版社',   108.00, '计算机', 5, 5, 0),
('9787111000002', '深入理解 Java 虚拟机',  '周志明',            '机械工业出版社',    99.00, '计算机', 3, 3, 0),
('9787111000003', 'Java 并发编程实战',     'Brian Goetz',      '机械工业出版社',    79.00, '计算机', 4, 4, 0),
('9787111000004', 'MySQL 技术内幕',        '姜承尧',            '机械工业出版社',    89.00, '数据库', 6, 6, 0),
('9787111000005', '高性能 MySQL',          'Baron Schwartz',   '电子工业出版社',    128.00, '数据库', 2, 2, 0),
('9787111000006', 'Redis 设计与实现',      '黄健宏',            '机械工业出版社',    85.00, '数据库', 5, 5, 0),
('9787111000007', 'Spring 实战',           'Craig Walls',      '人民邮电出版社',    89.00, '计算机', 4, 4, 0),
('9787111000008', '算法导论',              'Thomas H. Cormen', '机械工业出版社',   128.00, '计算机', 3, 3, 0),
('9787111000009', '计算机网络：自顶向下',   'James Kurose',     '机械工业出版社',    89.00, '计算机', 7, 7, 0),
('9787111000010', '操作系统概念',          'Abraham Silberschatz','机械工业出版社',  99.00, '计算机', 5, 5, 0),
('9787111000011', '代码整洁之道',          'Robert C. Martin', '人民邮电出版社',    59.00, '软件工程', 8, 8, 0),
('9787111000012', '重构：改善既有代码设计', 'Martin Fowler',    '人民邮电出版社',    99.00, '软件工程', 6, 6, 0),
('9787111000013', '设计模式',              'Erich Gamma',      '机械工业出版社',    69.00, '软件工程', 4, 4, 0),
('9787111000014', '领域驱动设计',          'Eric Evans',       '人民邮电出版社',    99.00, '软件工程', 3, 3, 0),
('9787111000015', '图解 HTTP',             '上野宣',            '人民邮电出版社',    49.00, '计算机', 9, 9, 0),
('9787111000016', '图解 TCP/IP',           '竹下隆史',          '人民邮电出版社',    59.00, '计算机', 6, 6, 0),
('9787111000017', '大话数据结构',          '程杰',              '清华大学出版社',    59.00, '计算机', 5, 5, 0),
('9787111000018', '数学之美',              '吴军',              '人民邮电出版社',    49.00, '科普',   4, 4, 0),
('9787111000019', '浪潮之巅',              '吴军',              '人民邮电出版社',    69.00, '科普',   3, 3, 0),
('9787111000020', '人月神话',              'Frederick Brooks', '清华大学出版社',    45.00, '软件工程', 2, 2, 0);

-- 验证
SELECT COUNT(*) AS book_count FROM book;
