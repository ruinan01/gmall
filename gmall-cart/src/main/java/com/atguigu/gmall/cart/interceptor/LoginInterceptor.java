package com.atguigu.gmall.cart.interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {

    // public String userId; 线程不安全 因为是单例的 且有状态参数

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Autowired
    private JwtProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("前置方法,.,.,.,.,.,.,.,.,.,.,");
        UserInfo userInfo = new UserInfo();

        String userKey = CookieUtils.getCookieValue(request, properties.getUserKey());
        if (StringUtils.isBlank(userKey)) {
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request,response,this.properties.getUserKey(),userKey,this.properties.getExpire());
        }
        userInfo.setUserKey(userKey);

        String token = CookieUtils.getCookieValue(request, properties.getCookieName());
        if (StringUtils.isNotBlank(token)) {
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());
            userInfo.setUserId(Long.valueOf(map.get("userId").toString()));
        }

        THREAD_LOCAL.set(userInfo);
        return true; // 都要放行 我们只是需要获取用户有没有登录的信息
    }

    public static UserInfo getUserInfo() {
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 这里我们使用了tomcat线程池,所以显示的删除线程的局部变量中的值,是必须的,否则会导致内存泄漏
        THREAD_LOCAL.remove();
    }
}
