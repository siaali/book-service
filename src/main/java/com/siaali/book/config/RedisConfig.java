package com.siaali.book.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key：字符串原样存
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());

        // value：对象转 JSON。
        //
        // 【enableUnsafeDefaultTyping() 是必须的，漏了就报 ClassCastException】
        //   不加这一句，存进 Redis 的 JSON 是纯数据：
        //     {"id":1,"name":"Java 编程思想",...}
        //   取出来时 Jackson 不知道它原来是什么类，只能还原成 LinkedHashMap，
        //   代码里 (Book) cached 就抛 ClassCastException。
        //
        //   加上之后，JSON 里会多一个类型字段：
        //     {"@class":"com.siaali.book.entity.Book","id":1,...}
        //   反序列化时按这个字段还原成 Book 对象。
        //
        //   注意 BigDecimal 这种字段也会被包一层：
        //     "price":["java.math.BigDecimal",108.00]
        //   这是正常的，因为 Jackson 也要靠它才知道还原成 BigDecimal 而不是 Double。
        //
        // 【为什么叫 Unsafe】
        //   它内部用的是"放行一切类型"的验证器（LaissezFaireSubTypeValidator）。
        //   如果攻击者能往 Redis 里写数据，就能构造一个指向危险类的 JSON，
        //   反序列化时触发远程代码执行 —— 这就是历史上多次反序列化漏洞的原理。
        //   生产环境应该用白名单：
        //     .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
        //             .allowIfSubType("com.siaali.book.entity")
        //             .build())
        //   本项目只允许 Book 一个类，为简化用了 Unsafe 版本，这一点要能讲清楚。
        GenericJacksonJsonRedisSerializer json = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);

        template.afterPropertiesSet();
        return template;
    }
}
