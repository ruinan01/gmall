package com.atguigu.gmall.order.interceptor;


import com.atguigu.gmall.common.bean.UserInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    // public String userId; 线程不安全 因为是单例的 且有状态参数

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserInfo userInfo = new UserInfo();
        String userId = request.getHeader("userId");
        userInfo.setUserId(Long.valueOf(userId));
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
