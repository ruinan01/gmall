package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "cart:info";

    private static final String PRICE_PREFIX = "cart:price";

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private CartAsyncService cartAsyncService;

    public void addCart(Cart cart) {

        String userId = this.getUserId();
        // 通过外层的key获取内层的map结构
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // 由于redis存储的是String类型 而我们通过cart点getSkuId是Long类型 所以不能直接判断 需要转变一下
        String skuId = cart.getSkuId().toString();
        BigDecimal count = cart.getCount();
        // 判断该用户的购物车中是否包含当前这条商品  是从redis中查询出来的 所有字段都是有的
        if (hashOps.hasKey(skuId)) {
            // 包含则更新数量   通过这个skuId获取到这个商品的json字符串
            String cartJson = hashOps.get(skuId).toString();
            // 反序列化为cart对象从而更新数量
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));
            // 再次序列化后写回redis  同时异步写到mysql
            hashOps.put(skuId,JSON.toJSONString(cart)); // 写入redis成功  可以和下面的else一样的一行代码优化写在本方法最外面

            // 异步多线程 我们不用编程式异步(代码实现 4种实现方式)
            // 我们使用声明式异步的方式(加注解)  springTask提供了声明式异步 简单好用 jvm级别安全性不高 够用 企业用的多 性能高

            // 写入mysql
            cartAsyncService.updateCart(userId,cart);
        } else {
            // 不包含,则新增一条记录 此时的cart时用户点击添加时只有 skuId,count两个字段传入 是不能直接存储 其它字段需要远程调用查询
            cart.setUserId(userId);

            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return;
            }
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());

            // 查询库存信息
            ResponseVo<List<WareSkuEntity>> listResponseVo = this.wmsClient.queryWareSkuBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = listResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {

                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            // 销售属性
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrsBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();  // 序列化之后放入
            cart.setSaleAttrs(JSON.toJSONString(skuAttrValueEntities));

            // 营销属性
            ResponseVo<List<ItemSaleVo>> saleResponseVo = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> sales = saleResponseVo.getData();
            cart.setSales(JSON.toJSONString(sales));

            cart.setCheck(true);

            hashOps.put(skuId,JSON.toJSONString(cart)); // 把用户添加到购物车的商品序列化存到redis
            // 异步存入数据库
            this.cartAsyncService.insertCart(userId,cart);

            // 添加价格缓存
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId,skuEntity.getPrice().toString());
        }

    }

    public Cart queryCartBySkuId(Long skuId) {  // 查询购物车
        String userId = this.getUserId();

        // 获取内层的map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        if (hashOps.hasKey(skuId.toString())) {  // 有这条商品
            // 获取购物车的json字符串
            String cartJson = hashOps.get(skuId.toString()).toString();
            return JSON.parseObject(cartJson,Cart.class); // 反序列化为Cart对象 返回
        }
        throw new CartException("此用户不存在这条购物车记录!!!");
    }

    private String getUserId() {
        // 获取登录信息.如果userId不为空,就以userId为key
        // 如果userId为空,就以userKey作为key
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        // String userId = userInfo.getUserId().toString(); // userId 没有登录为空点toString就出现异常了
        if (userInfo.getUserId() == null) {
            return userInfo.getUserKey();
        }
        return userInfo.getUserId().toString();
    }

    public List<Cart> queryCarts() {  // 不管有没有登录都要先查询购物车 若登录则合并未登录购物车再删除未登录购物车
        // 1 获取userKey
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();

        // 2 查询未登录的购物车 获取数据
        BoundHashOperations<String, Object, Object> unLoginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userKey);
        List<Object> unLoginCartJsons = unLoginHashOps.values(); // 获取内层map的value集合
        List<Cart> unLoginCarts = null;
        if (!CollectionUtils.isEmpty(unLoginCartJsons)) {  // 不为空反序列化转化为  未登录购物车集合给页面渲染
            unLoginCarts = unLoginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                // 从缓存中查询实时价格给每个商品
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());

        }

        // 3 获取userId,并判断userId是否为空,为空则直接返回未登录的购物车
        Long userId = userInfo.getUserId();
        if (userId == null) {
            return unLoginCarts;
        }

        // 4 若登录 获取登录状态的购物车内层map
        BoundHashOperations<String, Object, Object> loginHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);

        // 5 把未登录的购物车合并到登录状态的购物车的内存map中
        if (!CollectionUtils.isEmpty(unLoginCarts)) {  // 未登录状态购物车不为空
            unLoginCarts.forEach(   // 遍历未登录的购物车 (登录状态的购物车中包含的更新数量,不包含的新增记录)
                    cart -> {
                        BigDecimal count = cart.getCount();
                        String skuId = cart.getSkuId().toString();
                        if (loginHashOps.hasKey(skuId)) {
                            // 登录状态的购物车中包含该记录,更新数量
                            String cartJson = loginHashOps.get(skuId).toString();
                            cart = JSON.parseObject(cartJson, Cart.class);
                            cart.setCount(cart.getCount().add(count));
                            // 更新到mysql
                            this.cartAsyncService.updateCart(userId.toString(),cart);
                        } else {
                            // 登录状态的购物车中不包含该记录,新增一条记录
                            cart.setUserId(userId.toString()); // 一个是userId一个是userKey 要修改为userId
                            // 更新到mysql
                            this.cartAsyncService.insertCart(userId.toString(),cart);
                        }
                        // 更新到redis
                        loginHashOps.put(skuId,JSON.toJSONString(cart));  // 最终不管执行if还是else都是要存到redis的

                    });
            // 6 删除未登录的购物车(有未登录购物车才能删除)
            this.redisTemplate.delete(KEY_PREFIX + userKey);
            this.cartAsyncService.deleteCart(userKey);
        }
            // 7 查询登录状态的购物车并返回
        List<Object> loginCartJsons = loginHashOps.values(); // 判断是否为空 不为空反序列化为购物车集合返回
        if (!CollectionUtils.isEmpty(loginCartJsons)) {
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        return null;
    }

    public void updateNum(Cart cart) {  // 只有两个字段
        // 首先获取用户登录信息
        String userId = this.getUserId();
        BigDecimal count = cart.getCount();
        // 拿到内层的操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(cart.getSkuId().toString())) {  // 包含该记录才可能进行数量操作
            String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            hashOps.put(userId,JSON.toJSONString(cart));
            this.cartAsyncService.updateCart(userId,cart);  // redis中查出来的 其余字段还是有的 不会存在设置为空的情况
            return;
        }
        throw new CartException("该用户的购物车不包含该条记录");
    }

    public void deleteCart(Long skuId) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (hashOps.hasKey(skuId.toString())) {  // 有没有这条记录 有则删除
            hashOps.delete(skuId.toString());  // 删除redis中数据
            // 异步删除mysql中的数据
            this.cartAsyncService.deleteCartByUserIdAndSkuId(userId,skuId);
            return;
        }
        throw new CartException("该用户的购物车不包含该条记录");
    }

    public List<Cart> queryCheckedCartsByUserId(Long userId) {
        // 获取内层map
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        // 拿到购物车的json字符串集合
        List<Object> cartjsons = hashOps.values();
        if (CollectionUtils.isEmpty(cartjsons)) {
            throw new CartException("您没有购物车记录!");
        }
        return cartjsons.stream().map(cartjson -> JSON.parseObject(cartjson.toString(),Cart.class)).filter(Cart::getCheck).collect(Collectors.toList());
    }
}
