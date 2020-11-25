package com.atguigu.gmall.index.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD})  // 表示这个注解可以作用在方法上(也可以添加作用在类上)
@Retention(RetentionPolicy.RUNTIME)  // 运行时注解还是编译时注解 我们 这个表示是运行时注解
// @Inherited  是否可继承 我们这个不可继承 所以不添加
@Documented  // 是否要添加到项目文档中去
public @interface GmallCache {


    /**
     * 缓存key的前缀
     * 结构: 模块名 + ':' + 实例名 + ':'
     * 例如: 首页工程三级分类缓存
     * index:cates
     * @return
     */
    String prefix() default "gmall:cache";


    /**
     * 缓存的过期时间: 单位为分钟
     * @return
     */
    long timout() default 5L;

    /**
     * 防止缓存雪崩,给缓存时间添加随机值
     * 这里可以指定随机值范围
     */
    int random() default 5;

    /**
     * 为了防止缓存击穿,给缓存添加分布式锁
     * 这里可以指定 分布式的前缀
     * 最终分布式名称: lock _ 方法参数
     */

    String lock() default "lock:";
}
