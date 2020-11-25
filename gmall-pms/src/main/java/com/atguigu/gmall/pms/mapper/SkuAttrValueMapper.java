package com.atguigu.gmall.pms.mapper;

import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * sku销售属性&值
 * 
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-27 20:47:15
 */
@Mapper
public interface SkuAttrValueMapper extends BaseMapper<SkuAttrValueEntity> {

    public List<Map<String,Object>> querySaleAttrValuesMappingSkuId(Long spuId);
}
