package dao;

import entity.Book;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

// 手动继承BaseDao，实现BookDAO接口
public class BookDAOImpl extends BaseDao implements BookDAO {

    // 实现接口中的displayBook方法
    @Override
    public List<Book> displayBook() {
        List<Book> bookList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = getConnection();
            String sql = "SELECT * FROM book ORDER BY book_price ASC";
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();
            System.out.println("查询到 " + bookList.size() + " 本图书");
            while (rs.next()) {
                Book book = new Book();
                book.setBookId(rs.getInt("book_id"));
                book.setBookName(rs.getString("book_name"));
                book.setBookPrice(rs.getDouble("book_price"));
                book.setBookPublish(rs.getString("book_publish"));
                book.setBookAuthor(rs.getString("book_author"));
                book.setBookType(rs.getString("book_type"));
                bookList.add(book);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(rs, pstmt, conn);
        }
        return bookList;
    }

    // 实现接口中的addBook方法
    @Override
    public void addBook(Book book) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = getConnection();
            String createTableSql = "CREATE TABLE IF NOT EXISTS book (" +
                    "book_id INT PRIMARY KEY," +
                    "book_name VARCHAR(50) NOT NULL," +
                    "book_price DOUBLE," +
                    "book_publish VARCHAR(50)," +
                    "book_author VARCHAR(50)," +
                    "book_type VARCHAR(50))";
            pstmt = conn.prepareStatement(createTableSql);
            pstmt.executeUpdate();
            int rows = pstmt.executeUpdate();
            System.out.println("插入了 " + rows + " 条数据");
            String insertSql = "INSERT INTO book (book_id, book_name, book_price, " +
                    "book_publish, book_author, book_type) VALUES (?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(insertSql);
            pstmt.setInt(1, book.getBookId());
            pstmt.setString(2, book.getBookName());
            pstmt.setDouble(3, book.getBookPrice());
            pstmt.setString(4, book.getBookPublish());
            pstmt.setString(5, book.getBookAuthor());
            pstmt.setString(6, book.getBookType());
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(null, pstmt, conn);
        }
    }
}
