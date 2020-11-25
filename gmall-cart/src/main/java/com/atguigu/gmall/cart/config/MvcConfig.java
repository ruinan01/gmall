package com.atguigu.gmall.cart.config;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // registry(注册拦截器)添加拦截器对象 以及拦截路径
        registry.addInterceptor(loginInterceptor).addPathPatterns("/**");
    }
}
