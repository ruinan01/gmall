package com.atguigu.gmall.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableFeignClients
@MapperScan("com.atguigu.gmall.cart.mapper")
@EnableAsync // 开启异步  无需引入依赖 SpringTask
// 只要引入web启动器,自然就会引入springTask
public class GmallCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmallCartApplication.class, args);
    }

}
