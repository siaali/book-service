-- ============================================================
-- 造 10 万条借阅记录，用于深分页实验
-- 执行：mysql -u root -p book_db < scripts/seed-borrow-records.sql
-- ============================================================

-- MySQL 8 的递归查询默认最多 1000 层，造 10 万条必须先放宽，
-- 否则报 "Recursive query aborted after 1001 iterations"
SET SESSION cte_max_recursion_depth = 200000;

-- 先清空，保证实验从干净状态开始
TRUNCATE TABLE borrow_record;

INSERT INTO borrow_record (book_id, user_id, borrow_time, due_time, return_time, status)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT
    -- book_id 只取 1~20，对应 book 表里的 20 本书
    1 + (n % 20)                                        AS book_id,
    -- user_id 取 1~1000，模拟 1000 个用户
    1 + (n % 1000)                                      AS user_id,
    -- 借阅时间往前推 n 分钟，让数据在时间轴上分散开
    DATE_SUB(NOW(), INTERVAL n MINUTE)                  AS borrow_time,
    -- 应还时间 = 借出时间 + 30 天
    DATE_SUB(NOW(), INTERVAL (n - 30) MINUTE)           AS due_time,
    -- 每 3 条里有 1 条已归还（status=1），已归还的必须有 return_time
    CASE WHEN n % 3 = 0
         THEN DATE_SUB(NOW(), INTERVAL (n - 5) MINUTE)
         ELSE NULL END                                  AS return_time,
    CASE WHEN n % 3 = 0 THEN 1 ELSE 0 END               AS status
FROM seq;

-- 更新优化器的统计信息，否则它可能按"表里只有 0 行"来做执行计划
ANALYZE TABLE borrow_record;

SELECT COUNT(*) AS 总条数 FROM borrow_record;
SELECT status, COUNT(*) AS 条数 FROM borrow_record GROUP BY status;
