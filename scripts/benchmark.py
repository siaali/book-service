#!/usr/bin/env python3
"""
并发借书压测：验证 CAS 扣减能否防止超卖。

原理：
  用 threading.Barrier 让所有线程在同一个瞬间起跑，模拟真实的并发争抢。
  如果只是"循环发请求"，前一个请求早就返回了，根本构不成并发。

判定标准（三条全部满足才算通过）：
  1. 成功次数 == 初始库存
  2. 最终 available_stock == 0，且从不为负
  3. 借阅记录条数 == 初始库存

用法：
  python3 scripts/benchmark.py                     # 默认 200 并发，库存 10
  python3 scripts/benchmark.py --concurrency 500   # 改并发数
  python3 scripts/benchmark.py --stock 20          # 改初始库存
"""

import argparse
import json
import os
import subprocess
import threading
import time
import urllib.error
import urllib.request
from collections import Counter

BASE = "http://127.0.0.1:8080"
BORROW_URL = f"{BASE}/api/borrows"

# 数据库密码从环境变量读，不硬编码进代码。
# 仓库是 public 的，任何写死的凭据都等同于已泄露。
DB_PASSWORD = os.environ.get("DB_PASSWORD", "")


def mysql_query(sql: str) -> int:
    """执行一条查询并返回单个数字。用 -N -B 去掉表头和边框，便于程序解析。"""
    out = subprocess.run(
        ["mysql", "-u", "root", f"-p{DB_PASSWORD}", "-h", "127.0.0.1",
         "-D", "book_db", "-N", "-B", "-e", sql],
        capture_output=True, text=True,
    ).stdout.strip()
    return int(out) if out.isdigit() else -999


def borrow_once(user_id: int, book_id: int, barrier: threading.Barrier,
                results: list, latencies: list):
    """单个借书请求。结果码写进共享列表，不做任何统计计算（避免锁竞争影响压测）。"""
    body = json.dumps({"bookId": book_id, "userId": user_id}).encode("utf-8")
    req = urllib.request.Request(
        BORROW_URL,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    # 所有线程在这里等齐，然后同时冲出去
    try:
        barrier.wait(timeout=30)
    except threading.BrokenBarrierError:
        results.append("BARRIER_BROKEN")
        return

    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read())
            results.append(payload["code"])
    except urllib.error.HTTPError as e:
        results.append(f"HTTP_{e.code}")
    except Exception as e:
        results.append(f"EXC_{type(e).__name__}")
    finally:
        latencies.append((time.perf_counter() - start) * 1000)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--concurrency", type=int, default=200, help="并发线程数")
    parser.add_argument("--stock", type=int, default=10, help="每本书重置后的库存")
    parser.add_argument("--books", type=int, default=1,
                        help="参与压测的图书数量。1=全部请求抢同一本书（热点）；20=分散到 20 本（无热点）")
    args = parser.parse_args()

    n = args.concurrency
    books = args.books
    expected = args.stock * books

    print("=" * 62)
    if books == 1:
        print(f"压测配置：并发 {n}，图书 ID 1（单点热点），库存 {args.stock}")
    else:
        print(f"压测配置：并发 {n}，图书 ID 1~{books}（分散无热点），每本库存 {args.stock}")
    print("=" * 62)

    # 重置库存
    reset_sql = (
        f"UPDATE book SET total_stock={args.stock}, available_stock={args.stock}, version=0 "
        f"WHERE id <= {books}; DELETE FROM borrow_record WHERE book_id <= {books};"
    )
    subprocess.run(
        ["mysql", "-u", "root", f"-p{DB_PASSWORD}", "-h", "127.0.0.1", "-D", "book_db", "-e", reset_sql],
        capture_output=True,
    )
    print(f"库存已重置，总可借 {expected} 本，借阅记录已清空\n")

    results, latencies = [], []
    barrier = threading.Barrier(n)
    threads = []
    for i in range(n):
        # 按 i 轮转分配到不同图书；books=1 时全部打 1 号书，形成单点热点
        book_id = 1 + (i % books)
        threads.append(threading.Thread(
            target=borrow_once, args=(1000 + i, book_id, barrier, results, latencies)
        ))

    wall_start = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    wall = (time.perf_counter() - wall_start) * 1000

    # ---------- 结果统计 ----------
    counter = Counter(results)
    success = counter.get(200, 0)
    fail_stock = counter.get(400, 0)      # 库存不足
    fail_server = counter.get(500, 0)     # 服务端异常
    other = {k: v for k, v in counter.items() if k not in (200, 400, 500)}

    latencies.sort()
    p50 = latencies[len(latencies) // 2] if latencies else 0
    p95 = latencies[int(len(latencies) * 0.95)] if latencies else 0
    p99 = latencies[int(len(latencies) * 0.99)] if latencies else 0

    print("【请求结果】")
    print(f"  成功借出         {success}")
    print(f"  库存不足(400)    {fail_stock}")
    print(f"  服务端异常(500)  {fail_server}")
    if other:
        print(f"  其他             {other}")

    print("\n【耗时】")
    print(f"  总墙钟时间       {wall:.0f} ms")
    print(f"  P50 / P95 / P99  {p50:.0f} / {p95:.0f} / {p99:.0f} ms")
    print(f"  吞吐量           {n / (wall / 1000):.0f} 请求/秒")

    # ---------- 数据库最终状态 ----------
    final_stock = mysql_query(f"SELECT COALESCE(SUM(available_stock), 0) FROM book WHERE id <= {books}")
    final_records = mysql_query(f"SELECT COUNT(*) FROM borrow_record WHERE book_id <= {books}")

    print("\n【数据库最终状态】")
    print(f"  剩余库存合计     {final_stock}")
    print(f"  借阅记录数       {final_records}")

    # ---------- 判定 ----------
    print("\n【判定】")
    ok = True

    if success == expected:
        print(f"  [PASS] 成功借出 {success} 次 == 预期 {expected}（{args.stock} × {books} 本）")
    else:
        print(f"  [FAIL] 成功借出 {success} 次 != 预期 {expected}")
        ok = False

    if final_stock == 0:
        print("  [PASS] 最终库存精确归零，未出现负数")
    else:
        print(f"  [FAIL] 最终库存为 {final_stock}，预期 0")
        ok = False

    if final_records == success:
        print(f"  [PASS] 借阅记录 {final_records} 条 == 成功次数 {success}")
    else:
        print(f"  [FAIL] 借阅记录 {final_records} 条 != 成功次数 {success}（事务原子性被破坏）")
        ok = False

    if fail_server > 0:
        print(f"  [WARN] 有 {fail_server} 个请求返回 500，需查看应用日志")

    print()
    if ok:
        print("  结论：无超卖。CAS 条件更新在并发下正确串行化。")
    else:
        print("  结论：出现超卖或数据不一致，需要排查。")


if __name__ == "__main__":
    main()
