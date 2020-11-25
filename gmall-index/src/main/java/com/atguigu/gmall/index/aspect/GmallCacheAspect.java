package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.config.GmallCache;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect  // 声明是一个切面类
@Component  // 注册到spring容器
public class GmallCacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RBloomFilter<String> bloomFilter;


    /**
     * 获取目标方法参数: joinPoint.getArgs()
     * 获取目标方法签名: MethodSignature signature = (MethodSignature)joinPoint.getSignature()
     * 获取目标类: joinPoint.getTarget().getClass()
     * @param joinPoint
     * @return
     * @throws Throwable
     */

    // 环绕通知
    @Around("@annotation(com.atguigu.gmall.index.config.GmallCache)")  // 切一个注解
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        // System.out.println("环绕前通知..........");

        // 获取目标方法注解GmallCache注解对象
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        // 获取目标方法对象
        Method method = signature.getMethod();
        // 获取目标方法上的注解对象 就可以获取注解中的参数了
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        // 获取GmallCache中的前缀属性
        String prefix = gmallCache.prefix();
        // 获取方法的返回值类型
        Class<?> returnType = method.getReturnType();


        //获取目标方法的参数列表
        List<Object> args = Arrays.asList(joinPoint.getArgs());
        // 组装成缓存key
        String key = prefix + args;

        boolean flag = this.bloomFilter.contains(key);
        if (!flag) {
            return null;
        }

        // 先查询缓存,缓存中有直接反序列化后 直接返回
        String json = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseObject(json,returnType);
        }

        // 为了防止缓存击穿,可以添加分布式锁
        String lock = gmallCache.lock();
        RLock fairLock = this.redissonClient.getFairLock(lock + args);
        // 加锁
        fairLock.lock();

        try {
            // 再查缓存, 缓存中有 直接反序列化后,直接返回 防止其它线程已经查询到放入缓存
            String json2 = this.redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json2)) {
                return JSON.parseObject(json2,returnType);
            }

            // 执行目标方法,获取数据库数据
            Object result = joinPoint.proceed(joinPoint.getArgs());

            // 放入缓存  如果result为null,为了防止缓存穿透,依然放入缓存,但缓存时间极短
            if (result == null) {
                // this.redisTemplate.opsForValue().set(key,null,1, TimeUnit.MINUTES);
            } else {
                // 为了防止缓存雪崩,要给缓存时间添加随机值
                long timout = gmallCache.timout() + new Random().nextInt(gmallCache.random());
                this.redisTemplate.opsForValue().set(key,JSON.toJSONString(result),timout,TimeUnit.MINUTES);
            }


            // System.out.println("环绕后通知..........");
            return result;
        } finally {
            // 释放分布式锁
            fairLock.unlock();
        }
    }

    @Before("execution(* com.atguigu.gmall.index.service.*.*(..))")  // 前置通知
    // 切service下的所有包的所有方法 参数列表为任意 返回值类型为任意
    public void before(JoinPoint joinPoint) { // 也就是我没有什么 我需要什么我可以拦截下来添加前置通知
        joinPoint.getArgs();
    }

    @AfterReturning(value = "execution(* com.atguigu.gmall.index.service.*.*(..))",returning = "result")  // 返回后通知
    // 切service下的所有包的所有方法 参数列表为任意 返回值类型为任意
    // result : 目标方法的返回值 对这个返回值进行处理
    public void AfterReturning(JoinPoint joinPoint,Object result) { // 也就是我没有什么 我需要什么我可以拦截下来添加前置通知
        joinPoint.getArgs();
    }

    @AfterThrowing(value = "execution(* com.atguigu.gmall.index.service.*.*(..))",throwing = "ex")  // 异常后通知
    // 切service下的所有包的所有方法 参数列表为任意 返回值类型为任意
    // 根据异常来进行通知 如 判断出现了什么异常我才通知 进行更多的处理
    public void AfterThrowing(JoinPoint joinPoint,Throwable ex) {
        joinPoint.getArgs();
    }

    @After(value = "execution(* com.atguigu.gmall.index.service.*.*(..))")  // 最终通知
    // 切service下的所有包的所有方法 参数列表为任意 返回值类型为任意
    public void After(JoinPoint joinPoint) { // 最终都会执行
        joinPoint.getArgs();
    }
}
