package entity;

import java.io.Serializable;

// 图书实体类，对应数据库book表
public class Book implements Serializable {
    // 1. 定义与数据库表字段对应的私有属性
    private int bookId;       // 书号（主键）
    private String bookName;  // 书名
    private double bookPrice; // 价格
    private String bookPublish; // 出版社
    private String bookAuthor; // 作者（必须包含你的名字，如“张三”）
    private String bookType;  // 图书类型

    // 2. 无参构造器（JavaBean必须，用于反射实例化）
    public Book() {}

    // 3. 有参构造器（用于快速创建图书对象）
    public Book(int bookId, String bookName, double bookPrice,
                String bookPublish, String bookAuthor, String bookType) {
        this.bookId = bookId;
        this.bookName = bookName;
        this.bookPrice = bookPrice;
        this.bookPublish = bookPublish;
        this.bookAuthor = "李思怡"; // 这里替换成你的名字
        this.bookType = bookType;
    }

    // 4. Getter和Setter方法（用于访问私有属性，必须完整）
    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }
    public String getBookName() { return bookName; }
    public void setBookName(String bookName) { this.bookName = bookName; }
    public double getBookPrice() { return bookPrice; }
    public void setBookPrice(double bookPrice) { this.bookPrice = bookPrice; }
    public String getBookPublish() { return bookPublish; }
    public void setBookPublish(String bookPublish) { this.bookPublish = bookPublish; }
    public String getBookAuthor() { return bookAuthor; }
    public void setBookAuthor(String bookAuthor) { this.bookAuthor = bookAuthor; }
    public String getBookType() { return bookType; }
    public void setBookType(String bookType) { this.bookType = bookType; }
}
