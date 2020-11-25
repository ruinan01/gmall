package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.index.lock.DistributedLock;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;

    private static final String KEY_PREFIX = "index:cates:";

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedissonClient redissonClient;

    public List<CategoryEntity> queryLvl1Categories() {
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0L);
        return listResponseVo.getData();
    }

    @GmallCache(prefix = KEY_PREFIX,timout = 1296001,random = 14400,lock = "lock:cates:")
    public List<CategoryEntity> queryLvl2CategoriesWithSubsByPid(Long pid) {
            // 如果没有 执行业务远程调用 获取数据 最后放入缓存
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2CatesWithSubsByPid(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();
            return categoryEntities;
    }

    public List<CategoryEntity> queryLvl2CategoriesWithSubsByPid2(Long pid) {
        // 先查询缓存 如果有 命中直接返回
        String json = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)) {
            // 如果命中了 反序列化 返回
            return JSON.parseArray(json,CategoryEntity.class);
        }
        RLock lock = this.redissonClient.getLock("lock" + pid);  // 解决缓存击穿 一个热点key 如果不加pid那就是都锁住了
        lock.lock();
        // 加完锁之后再确认缓存中有没有 以免其它请求不知道 又去访问数据库了
        try {
            // 加锁过程中 可能已经有其它线程把数据放入缓存
            // 所以再次查询缓存 如果有 命中直接返回  但是此时锁没有释放 后续请求进不来了 所以放在try中
            String json2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
            if (StringUtils.isNotBlank(json2)) {
                // 如果命中了 反序列化 返回
                return JSON.parseArray(json2,CategoryEntity.class);
            }

            // 如果没有 执行业务远程调用 获取数据 最后放入缓存
            ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryLvl2CatesWithSubsByPid(pid);
            List<CategoryEntity> categoryEntities = listResponseVo.getData();

            if (CollectionUtils.isEmpty(categoryEntities)) {
                // 为了防止缓存穿透 数据即使为null也缓存 为了防止缓存数据过多,缓存时间设置的极短
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid,JSON.toJSONString(categoryEntities),1, TimeUnit.MINUTES);
            } else {
                // 为了防止缓存雪崩 给缓存时间添加随机值
                this.redisTemplate.opsForValue().set(KEY_PREFIX + pid,JSON.toJSONString(categoryEntities),2160 + new Random().nextInt(900), TimeUnit.HOURS);
            }

            return categoryEntities;
        } finally {
            lock.unlock();
        }
    }

    public void testLock2() {
        RLock lock = redissonClient.getLock("lock");
        lock.lock(30,TimeUnit.SECONDS);
        try {
            // 获取锁成功,执行业务逻辑,并最后释放锁
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                return;
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num));
        } finally {
            // lock.unlock();
        }

    }

    public void testLock() {

        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.distributedLock.tryLock("lock", uuid, 30);
        if (lock) {
            // 获取锁成功,执行业务逻辑,并最后释放锁
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                return;
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num));

            try {
                TimeUnit.SECONDS.sleep(180);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.testSub("lock",uuid);

        this.distributedLock.unLock("lock",uuid);

    }

    public void testSub(String lockName, String uuid) {
        this.distributedLock.tryLock(lockName,uuid,30);
        System.out.println("测试可重入的分布式锁");
        this.distributedLock.unLock(lockName,uuid);
    }

    public void testLock1() {
        // 尝试获取锁
        String uuid = UUID.randomUUID().toString();
        Boolean flag = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS);

        // 获取锁失败 重试
        if (!flag) {
            try {
                Thread.sleep(50);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            // this.redisTemplate.expire("lock",3,TimeUnit.SECONDS);
            // 获取锁成功,执行业务逻辑,并最后释放锁
            String numString = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isBlank(numString)) {
                return;
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num));

            // 释放锁 为了防止误删,删除之前需要判断是不是自己的锁
//            if (StringUtils.equals(uuid,this.redisTemplate.opsForValue().get("lock"))) {
//                this.redisTemplate.delete("lock");
//            }
            // 释放锁  判断当前的redis的锁是不是自己的 是自己的才可以删除(释放)
            String script = "if(redis.call('get',KEYS[1]) == ARGV[1]) then return redis.call('del',KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class), Arrays.asList("Lock"),uuid);
        }
    }

    public void testWrite() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.writeLock().lock(10,TimeUnit.SECONDS);

        System.out.println("模仿了写的操作....");
        // TODO 释放锁 (10秒自动释放了)
    }

    public void testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock(10,TimeUnit.SECONDS);

        System.out.println("模仿了读的操作....");
        // TODO 释放锁 (10秒自动释放了)
    }

    public void latch() throws InterruptedException {
        RCountDownLatch cdl = this.redissonClient.getCountDownLatch("latch");
        cdl.trySetCount(6);  // 设置等待数量
        cdl.await();
    }

    public void testCountdown() {
        RCountDownLatch cdl = this.redissonClient.getCountDownLatch("latch");
        cdl.countDown();// 执行一次countDown减1
    }
}
