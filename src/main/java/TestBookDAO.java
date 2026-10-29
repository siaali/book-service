import dao.BookDAO;
import dao.BookDAOImpl;
import entity.Book;
import java.util.List;

// 测试类：验证图书添加和查询功能
public class TestBookDAO {
    public static void main(String[] args) {
        // 1. 创建DAO实现类对象（通过接口引用，符合面向接口编程）
        BookDAO bookDAO = new BookDAOImpl();

        // 2. 添加测试数据（至少3条，其中1本作者为你的名字）
        Book myBook = new Book(1, "Java编程实战", 59.9, "机械工业出版社", "李思怡", "计算机"); // 替换“张三”为你的名字
        Book book2 = new Book(2, "数据结构与算法", 45.5, "人民邮电出版社", "李四", "计算机");
        Book book3 = new Book(3, "MySQL从入门到精通", 69.8, "清华大学出版社", "王五", "数据库");
        // 调用添加方法
        bookDAO.addBook(myBook);
        bookDAO.addBook(book2);
        bookDAO.addBook(book3);

        // 3. 查询所有图书并在控制台输出
        List<Book> books = bookDAO.displayBook();
        System.out.println("\n📚 图书列表（按价格升序）：");
        // 打印表头
        System.out.println("书号\t书名\t\t\t价格\t出版社\t\t\t作者\t类型");
        System.out.println("--------------------------------------------------------");
        // 遍历集合，格式化输出
        for (Book book : books) {
            System.out.printf("%d\t%s\t%.1f\t%s\t%s\t%s%n",
                    book.getBookId(),
                    book.getBookName(),
                    book.getBookPrice(),
                    book.getBookPublish(),
                    book.getBookAuthor(),
                    book.getBookType());
        }
    }
}