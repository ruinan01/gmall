package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallOmsClient omsClient;

    private static final String KEY_PREFIX = "order:token";

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 订单确认页
     * 由于存在大量的远程调用，这里使用异步编排做优化
     * @return
     */

    public OrderConfirmVo  confirm() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        // 获取登录信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();


        // 获取用户选中的购物车信息
        ResponseVo<List<Cart>> cartsResponseVo = this.cartClient.queryCheckedCartsByUserId(userId);
        List<Cart> carts = cartsResponseVo.getData();

        // 判断是否为空
        if (CollectionUtils.isEmpty(carts)) {
            new OrderException("您没有选中的购物车记录,请确认要结算的物品!");
        }
        // 把购物车记录转化成订单详情记录 : skuId,count
        List<OrderItemVo> itemVos = carts.stream().map(cart -> {
            OrderItemVo itemVo = new OrderItemVo();
            itemVo.setCount(cart.getCount());

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {

                itemVo.setSkuId(cart.getSkuId());
                itemVo.setTitle(skuEntity.getTitle());
                itemVo.setDefaultImage(skuEntity.getDefaultImage());
                itemVo.setWeight(skuEntity.getWeight());
                itemVo.setPrice(skuEntity.getPrice());
            }

            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrsBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            itemVo.setSaleAttrs(skuAttrValueEntities);

            ResponseVo<List<ItemSaleVo>> saleResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = saleResponseVo.getData();
            itemVo.setSales(itemSaleVos);

            return itemVo;
        }).collect(Collectors.toList());
        confirmVo.setOrderItems(itemVos);

        // 获取用户的收货地址列表
        ResponseVo<List<UserAddressEntity>> addressesResponseVo = this.umsClient.queryAddressesByUserId(userId);
        List<UserAddressEntity> addresses = addressesResponseVo.getData();
        confirmVo.setAddresses(addresses);


        // 根据用户id查询用户信息(购买积分)
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null) {
            confirmVo.setBounds(userEntity.getIntegration());
        }

        // 生成orderToken
        String orderToken = IdWorker.getTimeId();
        confirmVo.setOrderToken(orderToken);
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken,orderToken,24, TimeUnit.HOURS);

        return confirmVo;
    }

    public String submit(OrderSubmitVo submitVo) {
        // 1 防重  (保证原子性 判断 立马删除 且 同时成功 不然高并发情况下都来判断都可以删除 出现了重复提交情况)
        String orderToken = submitVo.getOrderToken();
        if (StringUtils.isBlank(orderToken)) {
            throw new OrderException("请求不合法!");
        }
        String script = "if(redis.call('exists',KEYS[1]) == 1) then return redis.call('del',KEYS[1]) else return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), "");
        if (!flag) {
            throw new OrderException("页面已过期或者您已提交!");
        }
        // 2 验总价
        List<OrderItemVo> items = submitVo.getItems();  // 获取订单详情 / 商品清单
        if (CollectionUtils.isEmpty(items)) {
            throw new OrderException("您没有选中的购物车记录");
        }
        // 从数据库查询商品价格获取总计 (数据库的实时价格)
        BigDecimal currentTotalPrice = items.stream().map(item -> { // 遍历每个商品 获得总价 最终累加 比价
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                return skuEntity.getPrice().multiply(item.getCount());
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();
        BigDecimal totalPrice = submitVo.getTotalPrice(); // 页面实时价格
        if (totalPrice.compareTo(currentTotalPrice) != 0) {
            throw new OrderException("页面已过期,请刷新后重试");
        }
        // 3 验库存并锁库存
        List<SkuLockVo> lockVos = items.stream().map(item -> {
            SkuLockVo lockVo = new SkuLockVo();
            lockVo.setSkuId(item.getSkuId());
            lockVo.setCount(item.getCount().intValue());
            return lockVo;
        }).collect(Collectors.toList());

        // 返回null表示成功
        ResponseVo<List<SkuLockVo>> lockResponseVo = this.wmsClient.checkAndLock(lockVos, orderToken);
        List<SkuLockVo> skuLockVos = lockResponseVo.getData(); // 为空说明所有商品都锁定成功了
        if (!CollectionUtils.isEmpty(skuLockVos)) { // 不为空锁定失败了 返回整个信息列表 像京东一样 没货显示灰色 有货就是有货
            throw new OrderException(JSON.toJSONString(skuLockVos));
        }

        // 4 创建订单 (oms 的保存订单 和订单详情的方法)
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();
        try {
            this.omsClient.saveOrder(submitVo,userId);
        } catch (Exception e) {
            e.printStackTrace();
            // TODO 异步标记为无效订单 直接响应给客户 不用阻塞了
        }


        // 5 删除购物车
        return null;
    }
}
