package com.atguigu.gmall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

@Configuration   // 首先声明它是一个配置类
public class CorsConfig {

    @Bean  // 把这个过滤器初始化到spring容器
    public CorsWebFilter corsWebFilter() {

        // 初始化CORS配置对象
        CorsConfiguration config = new CorsConfiguration();
        // 允许的域,不要写* 否则cookie就无法使用了
        config.addAllowedOrigin("http://manager.gmall.com");
        config.addAllowedOrigin("http://www.gmall.com");
        config.addAllowedOrigin("http://gmall.com");
        // 允许的头信息
        config.addAllowedHeader("*");
        // 允许的请求方式
        config.addAllowedMethod("*");
        // 是否允许携带Cookie信息 如果允许携带cookie: origin不能为*,并且该参数必须是true
        config.setAllowCredentials(true);

        // 添加映射路径,我们拦截一切请求
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        corsConfigurationSource.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(corsConfigurationSource);
    }
}
