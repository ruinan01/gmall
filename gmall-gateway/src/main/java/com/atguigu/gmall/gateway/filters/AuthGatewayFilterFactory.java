package com.atguigu.gmall.gateway.filters;


import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import com.google.common.net.HttpHeaders;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@EnableConfigurationProperties(JwtProperties.class)
@Component
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {

    @Autowired
    private JwtProperties jwtProperties;

    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("paths");
    }

    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                System.out.println("我是局部过滤器,我只拦截特定路由对应的服务请求");
                System.out.println("局部过滤器获取配置信息: key=" + config.getPaths());
                // 开始实现业务逻辑

                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();

                // 1 判断当前请求的路径是否在拦截名单之中,不在则直接放行
                String curPath = request.getURI().getPath();  // 当前请求的路径
                List<String> paths = config.getPaths(); // 拦截名单
                if (!paths.stream().anyMatch(path -> curPath.startsWith(path))) { // 如果没有一个满足的
                    return chain.filter(exchange);   // 直接放行
                }
                // 在拦截名单中

                // 2 获取请求中的token信息,异步-头信息,同步-cookie
                String token = request.getHeaders().getFirst("token"); // 异步 通过头信息来去获取
                if (StringUtils.isBlank(token)) {  // 没有获取到,再尝试从cookie中获取
                    MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                    // cookies不为空 且 包含我这个cookies名称 "GMALL-TOKEN"
                    if (!CollectionUtils.isEmpty(cookies) && cookies.containsKey(jwtProperties.getCookieName())) {
                        HttpCookie cookie = cookies.getFirst(jwtProperties.getCookieName());
                        token = cookie.getValue();
                    }
                }

                // 3 判断token是否为空, 为空则重定向到登录页面
                if (StringUtils.isBlank(token)) {
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete(); // 把响应结束(拦截后续业务逻辑)
                }

                try {
                    // 4 解析token信息,如果出现异常重定向到登录页面
                    Map<String, Object> map = JwtUtils.getInfoFromToken(token, jwtProperties.getPublicKey());
                    // 5 拿到载荷中ip和当前请求的ip比较,不一致说明被盗用,直接重定向到登录页面
                    String ip = map.get("ip").toString();  // 用户ip
                    String curIp = IpUtils.getIpAddressAtGateway(request);  // 当前请求的ip
                    if (!StringUtils.equals(ip,curIp)) {
                        // ip不一致 重定向到登录页面
                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                        return response.setComplete(); // 把响应结束(拦截后续业务逻辑)
                    }

                    // 6 把解析到的用户登录信息传递给后续服务 (一系列操作都走的http协议 一般我们使用request对象给后续服务传递信息)
                    // 点header 设置头信息 后续服务可能用到用户id 所以放进去
                    request.mutate().header("userId",map.get("userId").toString()).build();
                    exchange.mutate().request(request).build(); // 后续服务需要exchange对象 所以还要把request传进去构建
                    // 放行

                    return chain.filter(exchange);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 解析出现异常,重定向到登录页面
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete(); // 把响应结束(拦截后续业务逻辑)
                }
            }
        };
    }


    @Data
    public static class PathConfig { // 自定义一个静态内部类,声明接收配置信息的字段
        private List<String> paths;
    }
}
