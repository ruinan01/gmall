package com.atguigu.gmall.pms.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SkuAttrValueMapperTest {

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Test
    void querySaleAttrValuesMappingSkuId() {
        List<Map<String, Object>> map = this.skuAttrValueMapper.querySaleAttrValuesMappingSkuId(7L);
        System.out.println(map);
    }
}