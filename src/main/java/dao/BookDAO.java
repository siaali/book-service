package dao;

import entity.Book;
import java.util.List;

// 图书DAO接口：定义数据访问方法
public interface BookDAO {
    // 1. 查询所有图书（按价格升序排列）
    List<Book> displayBook();

    // 2. 添加图书到数据库
    void addBook(Book book);
}
