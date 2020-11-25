package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "stock:lock";

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> lockVos,String orderToken) {

        if (CollectionUtils.isEmpty(lockVos)) {
            throw new OrderException("没有要购买的商品");
        }
        //一次性遍历完所有送货清单,验库存并锁库存
        lockVos.forEach(lockVo -> {  // 要一次遍历完 让用户知道哪些库存不足 不可以遇到库存不足的就不遍历后面的了
            this.checkLock(lockVo);
        });

        // 只要有一个锁定失败了 就解锁所有锁定成功的 以防用户回来再锁一次  // 这里怎么是isLock!!!
        if (lockVos.stream().anyMatch(lockVo -> !lockVo.isLock())) {
            // 解锁所有
            List<SkuLockVo> successLockVos = lockVos.stream().filter(SkuLockVo::isLock).collect(Collectors.toList());
            successLockVos.stream().forEach(lockVo -> {
                this.wareSkuMapper.unLock(lockVo.getWareSkuId(),lockVo.getCount());
            });
            return lockVos;  // 返回锁定失败的
        }
        // 锁定成功要缓存到redis中  30分钟不支付要解锁库存
        // 不缓存在redis中 谁知道锁了那个仓库那个物品多少呢? 方便将来解锁库存
        // 且 用户支付之后减库存 也需要这个订单锁定信息
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, JSON.toJSONString(lockVos));

        return null;  // 锁定成功 返回null
    }

    private void checkLock(SkuLockVo lockVo) {
        RLock fairLock = this.redissonClient.getFairLock("stock:" + lockVo.getSkuId());
        fairLock.lock();

        // 1 查询库存信息
        List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.checkLock(lockVo.getSkuId(), lockVo.getCount());
        if (CollectionUtils.isEmpty(wareSkuEntities)) {
            // 如果没有仓库满足购买要求,设置锁定失败
            lockVo.setLock(false);
            // 释放锁
            fairLock.unlock();
            return;
        }
        // 2 锁定库存信息 : 假装大数据分析完成
        WareSkuEntity wareSkuEntity = wareSkuEntities.get(0);// 没有大数据分析 就随便选的第一个
        // 锁定库存
        if(this.wareSkuMapper.lock(wareSkuEntity.getId(),lockVo.getCount()) == 1) {
            lockVo.setLock(true);
            lockVo.setWareSkuId(wareSkuEntity.getId());  // id 还是wareId ? 都可以?
        }
        fairLock.unlock();
    }
}