package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// DAO基类：封装数据库连接与资源关闭
public class BaseDao {
    // Derby嵌入式数据库配置（固定写法，无需修改）
    private static final String URL = "jdbc:derby:bookDB;create=true";
    // Derby嵌入式模式无需用户名和密码，留空
    private static final String USER = "";
    private static final String PASSWORD = "";

    // 方法1：获取数据库连接
    public Connection getConnection() {
        Connection conn = null;
        try {
            // 加载Derby驱动（JDK1.8内置，这行可省略，但保留更稳妥）
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            // 建立连接（URL中“create=true”表示若数据库不存在则自动创建）
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("✅ 数据库连接成功！");
        } catch (Exception e) {
            // 连接失败时打印错误信息
            e.printStackTrace();
            System.err.println("❌ 数据库连接失败：" + e.getMessage());
        }
        return conn;
    }

    // 方法2：关闭数据库资源（ResultSet→Statement→Connection，顺序不能乱）
    public void closeResources(ResultSet rs, Statement stmt, Connection conn) {
        try {
            if (rs != null) rs.close(); // 先关结果集
            if (stmt != null) stmt.close(); // 再关SQL执行对象
            if (conn != null) conn.close(); // 最后关连接
            System.out.println("✅ 数据库资源已关闭！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
