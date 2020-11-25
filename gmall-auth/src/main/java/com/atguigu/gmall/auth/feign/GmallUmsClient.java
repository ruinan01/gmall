package com.atguigu.gmall.auth.feign;

import com.atguigu.gmall.ums.api.GmallUmsApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("ums-service") //绑定feign客户端和要远程访问的服务名称
// GmallUmsClient 也就是auth的feign 继承了ums-interface的GmallUmsApi也就是api
// 远程访问了ums-service(gmall-ums)
public interface GmallUmsClient extends GmallUmsApi {
}
