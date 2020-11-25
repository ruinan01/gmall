package com.atguigu.gmall.index.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DistributedLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Thread thread;

    public Boolean tryLock(String lockName, String uuid, Integer expireTime) {
        // 获取锁
        String script = "if(redis.call('exists', KEYS[1]) == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1) then redis.call('hincrby', KEYS[1], ARGV[1], 1); redis.call('expire', KEYS[1], ARGV[2]); return 1; else return 0; end;";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid, expireTime.toString());
        if (!flag) {
            try {
                Thread.sleep(50);
                tryLock(lockName, uuid, expireTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        this.renewTime(lockName, uuid, expireTime);
        // 服务器宕机情况下 就算执行到这里也不会续期了 所以和锁不设置过期时间(无限时间)是两码事
        // 锁无限时间肯定会造成死锁的

        return true;
    }

    public void unLock(String lockName, String uuid) {
        // 释放锁
        String script = "if(redis.call('hexists', KEYS[1], ARGV[1]) == 0) then return nil; elseif(redis.call('hincrby', KEYS[1], ARGV[1], -1) == 0) then redis.call('del', KEYS[1]); return 1; else return 0; end;";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(lockName), uuid);
        if (flag == null) {
            throw new RuntimeException("您要释放的锁不存在,或在尝试释放别人的锁!");
        }
        thread.interrupt();  // 销毁这个狗线程
    }

    // 自动续期 只能在服务器运行的情况下才会自动续期
    private void renewTime(String lockName, String uuid,Integer expireTime) {
        String script = "if(redis.call('hexists', KEYS[1], ARGV[1]) == 1) then redis.call('expire', KEYS[1], ARGV[2]); return 1; else return 0; end";
        thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(expireTime * 2000 / 3);
                    this.redisTemplate.execute(new DefaultRedisScript<>(script,Boolean.class),Arrays.asList(lockName),uuid,expireTime.toString());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        },"");
        thread.start();
    }
}
