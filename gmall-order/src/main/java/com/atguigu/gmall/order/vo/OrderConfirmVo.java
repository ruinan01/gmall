package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    private List<UserAddressEntity> addresses;  //  用户地址信息 默认显示一条

    private List<OrderItemVo> orderItems; // 为了和购物车解耦合 List集合类型不可以使用Cat类型

    private Integer bounds; // 积分

    private String orderToken; //保证接口幂等性/ 防止重复提交的Token 一旦提交订单 这个Token就从redis中删除
    // TODO: 发票
    // 还有一些平台的营销信息 优惠券之类的 哪些商品适合什么优惠券.. 都不做了
}
