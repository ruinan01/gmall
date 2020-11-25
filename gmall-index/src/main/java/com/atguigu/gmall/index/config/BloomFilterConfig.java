package com.atguigu.gmall.index.config;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.google.common.hash.BloomFilter;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Configuration
public class BloomFilterConfig {
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private GmallPmsClient pmsClient;

    private static final String KEY_PREFIX = "index:cates:[";

    @Bean
    public RBloomFilter<String> bloomFilter() {
        RBloomFilter<String> bloomFilter = this.redissonClient.getBloomFilter("index:cates:bloom");
        bloomFilter.tryInit(10000,0.003); // 初始化布隆过滤器
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l); // 从数据库中查询一级分类
        List<CategoryEntity> categories = listResponseVo.getData();
        if (!CollectionUtils.isEmpty(categories)) {
            categories.forEach(categoryEntity -> {
                bloomFilter.add(KEY_PREFIX + categoryEntity.getId() + "]");
            });
        }
        return bloomFilter;
    }

    @Scheduled
    public void testFlushBloom() {  // 定时任务 定时获取这个过滤器里面的内容
        // key 是相同的 我把key相同的布隆过滤器更新了 那么spring里面的肯定也更新了
        RBloomFilter<String> bloomFilter = this.redissonClient.getBloomFilter("index:cates:bloom");
        bloomFilter.tryInit(10000,0.003); // 初始化布隆过滤器
        ResponseVo<List<CategoryEntity>> listResponseVo = this.pmsClient.queryCategoriesByPid(0l); // 从数据库中查询一级分类
        List<CategoryEntity> categories = listResponseVo.getData();
        if (!CollectionUtils.isEmpty(categories)) {
            categories.forEach(categoryEntity -> {
                bloomFilter.add(KEY_PREFIX + categoryEntity.getId() + "]");
            });
        }
    }
}
