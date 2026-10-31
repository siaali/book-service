package com.siaali.book;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 图书借阅服务启动类。
 *
 * 由《Java 程序设计》课程实验（原生 JDBC + Derby + JSP）重构而来，
 * 详见 README 的「演进过程」一节。
 */
@SpringBootApplication
@MapperScan("com.siaali.book.mapper")
@EnableScheduling
public class BookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookServiceApplication.class, args);
    }
}
