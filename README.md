# book-service 图书借阅服务

一个从课程实验演进而来的 Java 后端项目。

**起点**：《Java 程序设计》课程实验六 —— 原生 JDBC + Derby 嵌入式库 + JSP，216 行，只实现了「查列表 + 新增」。
**现状**：Spring Boot 4.1 + MyBatis-Plus + MySQL 8，分层架构，统一返回体与异常处理，分页查询。

保留完整 Git 提交历史，可以看到它是怎么一步步改过来的，而不是一次性生成的。

---

## 技术栈

| 层次 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 25 (LTS) |
| 框架 | Spring Boot | 4.1.2 |
| ORM | MyBatis-Plus | 3.5.18 |
| 数据库 | MySQL | 9.3 |
| 构建 | Maven | 3.9.6（wrapper） |
| 测试 | JUnit 5 | Boot 托管 |

---

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/books?page=1&size=10&keyword=Java` | 分页 + 书名/作者模糊搜索 |
| GET | `/api/books/{id}` | 详情 |
| POST | `/api/books` | 新增（含参数校验） |
| GET | `/api/books/ping` | 连通性自检，不依赖数据库 |
| POST | `/api/borrows` | 借书，`{"bookId":1,"userId":100}` |
| POST | `/api/borrows/return` | 还书，`{"recordId":1,"userId":100}` |
| GET | `/api/borrows?userId=100` | 查询某人当前借出未还的记录 |

### 示例

```bash
curl "http://localhost:8080/api/books?page=1&size=2&keyword=Java"
```

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "records": [
      { "id": 1, "isbn": "9787111000001", "name": "Java 编程思想", "author": "Bruce Eckel",
        "price": 108.00, "totalStock": 5, "availableStock": 5, "version": 0 }
    ],
    "total": 20,
    "current": 1,
    "size": 2,
    "pages": 10
  }
}
```

参数校验失败：

```json
{ "code": 400, "message": "name 书名不能为空", "data": null }
```

---

## 目录结构

```
src/main/java/com/siaali/book/
├── BookServiceApplication.java   启动类
├── controller/                   BookController、BorrowController
├── service/                      接口
│   └── impl/                     BookServiceImpl、BorrowServiceImpl
├── mapper/                       BookMapper、BorrowRecordMapper（继承 BaseMapper + 手写条件更新 SQL）
├── entity/                       Book、BorrowRecord
├── dto/                          BookCreateDTO、BorrowCreateDTO、ReturnDTO、PageResult
├── common/                       Result、BizException、GlobalExceptionHandler、BorrowStatus
└── config/                       MybatisPlusConfig（分页插件）
src/main/resources/
├── application.yml
└── db/schema.sql                 表结构 + 20 条测试数据
```

---

## 本地运行

### 1. 准备数据库

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 配置密码

密码通过环境变量注入，不写进仓库：

```bash
export DB_PASSWORD=你的密码
```

或直接改 `application.yml` 里 `password` 的默认值。

### 3. 启动

```bash
./mvnw spring-boot:run
```

访问 <http://localhost:8080/api/books/ping>，返回 `{"code":200,"message":"ok","data":"pong"}` 即成功。

### 4. 测试

```bash
./mvnw test
```

---

## 数据库设计

**book**（图书）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| isbn | VARCHAR(20) UNIQUE | 唯一索引，防重复录入 |
| name / author | VARCHAR | 各建普通索引，列表页模糊查询用 |
| price | DECIMAL(10,2) | **不用 double**，金额浮点会丢精度 |
| total_stock | INT | 总库存 |
| available_stock | INT | 可借库存，并发扣减改这个字段 |
| version | INT | 乐观锁版本号 |

**borrow_record**（借阅记录）

`idx_user_status(user_id, status)` 支持「某人当前借了哪些书」，
`idx_book_status(book_id, status)` 支持「某本书被谁借着」。

---

## 并发控制：库存扣减与幂等

### 问题一：超卖

「先查库存，判断大于 0，再更新」是课程实验里的常规写法，但它在并发下会超卖：

```java
Book book = bookMapper.selectById(bookId);
if (book.getAvailableStock() > 0) {          // ① 两个线程都读到 1
    book.setAvailableStock(book.getAvailableStock() - 1);
    bookMapper.updateById(book);             // ② 都写入 0
}
```

查和改之间有间隙，这被称为**检查后使用（Check-Then-Act）竞态**。库存 1 本，两个人同时借，都判断「够借」，结果借出 2 本。

**解法：把判断条件写进 UPDATE，让数据库原子执行。**

```sql
UPDATE book SET available_stock = available_stock - 1, version = version + 1
WHERE id = ? AND available_stock > 0
```

MySQL 执行 UPDATE 时对该行加排他锁，两个语句自动排队。第二个执行时 `available_stock` 已是 0，WHERE 不匹配，影响 0 行。应用层据此返回「库存不足」。

### 为什么不用 version 乐观锁

`version` 乐观锁适用于「**先读出来 → 在内存里计算 → 再写回去**」的场景（比如用户在页面上修改图书信息）。

而库存扣减是「**在数据库里直接做减法**」，`SET available_stock = available_stock - 1` 这条语句本身就在数据库内部完成了读-算-写，中间不会被打断。再叠一个 `version` 属于重复保护，只会多一次失败重试的可能。

### 问题二：重复归还

用户手抖点两次「还书」，或网络超时前端重试，库存会被加 2 次。

**解法：把状态判断写进 WHERE。**

```sql
UPDATE borrow_record SET status = 1, return_time = ?
WHERE id = ? AND user_id = ? AND status = 0
```

第一次 `status = 0` 匹配，影响 1 行，库存 +1。第二次 `status` 已是 1，影响 0 行，库存不动。这叫**幂等（idempotent）**。

同时 `user_id` 条件防止了**水平越权**：A 用户拿到 B 用户的 `recordId` 也无法替别人还书。

### 问题三：事务边界

借书要同时改两张表（扣库存、写记录），必须原子。事务加在 **Service 层**，因为只有 Service 知道这个业务动作涉及几张表。

```java
@Transactional(rollbackFor = Exception.class)
public Long borrow(BorrowCreateDTO dto) { ... }
```

`rollbackFor = Exception.class` 必须写：Spring 默认只对 `RuntimeException` 回滚，受检异常会照常提交，导致数据只写一半。

### 怎么验证事务真的生效

不是「跑通了就算对」，而是主动构造失败路径：

```bash
# 1. 记录库存（假设为 5）
# 2. 把借阅记录表改名，让「插入记录」必然失败
mysql -e "RENAME TABLE borrow_record TO borrow_record_tmp;"
# 3. 调借书接口 → 返回 500
# 4. 查库存 → 仍是 5（第一步的扣减被回滚了）
mysql -e "RENAME TABLE borrow_record_tmp TO borrow_record;"
```

如果不写 `@Transactional`，第 4 步会看到库存变成 4 而记录不存在 —— 这本书就「消失」了。

---

## 从课程实验到当前版本：修掉了什么

原版（commit `0cd07da`）有 9 处缺陷，其中 3 处**编译器和运行时都不会报错**，属于静默失败。

### 1. 构造方法把 `bookAuthor` 入参吃掉了

```java
public Book(int bookId, String bookName, double bookPrice,
            String bookPublish, String bookAuthor, String bookType) {
    ...
    this.bookAuthor = "李思怡";   // 入参 bookAuthor 从未被使用
}
```

测试数据里传了「李四」「王五」，入库后作者全是「李思怡」。
编译器不报错（未使用参数不是错误），IDE 最多把参数名标灰。

**教训**：方法入参要么用掉，要么删掉，不留着不用。

### 2. 统计打印在遍历之前，恒为 0

```java
rs = pstmt.executeQuery();
System.out.println("查询到 " + bookList.size() + " 本图书");  // 此时 list 还是空的
while (rs.next()) { ... bookList.add(book); }
```

控制台永远显示「查询到 0 本」，但下面明明列出了 3 本。
这个 bug 会把排查方向带偏 —— 你会以为是 SQL 错了或数据库没数据。

**教训**：日志要打在状态变更之后。日志位置错了比没有日志更危险。

### 3. DDL 执行两次，`rows` 拿的是建表语句的返回值

```java
pstmt = conn.prepareStatement(createTableSql);
pstmt.executeUpdate();              // 第一次建表
int rows = pstmt.executeUpdate();   // 又建了一次，DDL 返回 0
System.out.println("插入了 " + rows + " 条数据");   // 永远打印 0
```

`executeUpdate()` 的返回值是「受影响行数」，DML 返回行数，**DDL 返回 0**。
而且第一个 `PreparedStatement` 被直接覆盖，没有 close，泄漏了。

**教训**：建表是 DDL，不该出现在业务方法里，应由初始化脚本负责。

### 其他 6 处

| 问题 | 现在怎么处理 |
|---|---|
| `getConnection()` 吞异常返回 null，下游 NPE 掩盖真实原因 | 交给 HikariCP + Spring 事务管理，异常正常上抛 |
| 无连接池，插 3 条数据建 3 次物理连接 | HikariCP 连接池（maximum-pool-size=10） |
| `book_id` 手工指定无自增，重跑必主键冲突 | `IdType.AUTO` |
| 金额用 `double` | `DECIMAL(10,2)` + `BigDecimal` |
| Derby 用相对路径 `jdbc:derby:bookDB;create=true`，换目录就是另一个库 | MySQL + 固定 JDBC URL |
| `pom.xml` 缺 Derby 依赖，换台机器只靠 pom 跑不起来 | 依赖完整，`./mvnw test` 可直接验证 |

---

## 踩过的坑（环境层面）

1. **JDK 23 起 javac 不再隐式运行注解处理器** —— 不配 `annotationProcessorPaths`，Lombok 的 `@Data` 不生成 getter/setter，编译报「找不到符号 getId()」。这不是 Lombok 版本问题。
2. **环境变量 `SERVER__PORT` 会覆盖 `application.yml` 的 `server.port`** —— Spring Boot 宽松绑定把双下划线当点，且环境变量优先级高于配置文件。命令行参数 `--server.port=8080` 优先级最高。
3. **MyBatis-Plus 3.5.9 起分页插件被拆到 `mybatis-plus-jsqlparser`** —— 不引入这个依赖，`selectPage` 不会拼 LIMIT，会全表捞出来在内存里分页。
4. **Maven 直连 `repo.maven.apache.org` 报 `SSLHandshakeException`** —— 需要配阿里云镜像（`~/.m2/settings.xml`）。

---

## 后续计划

- [x] 借阅 / 归还接口，库存基于 `UPDATE ... WHERE available_stock > 0` 做 CAS 扣减
- [x] 事务原子性验证（构造失败路径，确认库存回滚）
- [ ] 并发压测，验证零超卖（200 并发借同一本书）
- [ ] 借阅记录查询（按用户 / 按图书）
- [ ] Redis 缓存图书详情，空值缓存 + 随机 TTL 防穿透/雪崩
- [ ] 逾期未还的定时任务扫描
