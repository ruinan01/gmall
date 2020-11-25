package com.atguigu.gmall.wms.mapper;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品库存
 * 
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-28 09:00:12
 */
@Mapper
public interface WareSkuMapper extends BaseMapper<WareSkuEntity> {

    public List<WareSkuEntity> checkLock(@Param("skuId") Long skuId,@Param("count") Integer count);

    public int lock(@Param("id")Long id, @Param("count")Integer count);

    public int unLock(@Param("id")Long id, @Param("count")Integer count);
}
