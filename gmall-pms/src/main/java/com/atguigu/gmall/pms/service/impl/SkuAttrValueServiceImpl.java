package com.atguigu.gmall.pms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.springframework.util.CollectionUtils;


@Service("skuAttrValueService")
public class SkuAttrValueServiceImpl extends ServiceImpl<SkuAttrValueMapper, SkuAttrValueEntity> implements SkuAttrValueService {

    @Autowired
    private AttrMapper attrMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuAttrValueMapper attrValueMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuAttrValueEntity> querySearchSkuAttrValueBySkuIdAndCid(Long skuId, Long cid) {
        // 第一步: 先查询 检索类型的规格参数
        List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("search_type", 1).eq("category_id", cid));
        if (CollectionUtils.isEmpty(attrEntities)) {
            return null;
        }
        List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());

        // 第二步: 检索规格参数
        return this.list(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).in("attr_id", attrIds));
    }

    @Override
    public List<SaleAttrValueVo> querySaleAttrValuesBySpuId(Long spuId) {
        // 通过spuId获取到这个spu下面的所有sku
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id",spuId));
        if (CollectionUtils.isEmpty(skuEntities)) {
            return null;
        }
        // 通过所有的sku值获取每个sku的id
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());
        // 查询通过skuId查询 sku对应的销售属性
        List<SkuAttrValueEntity> skuAttrValueEntities = this.list(new QueryWrapper<SkuAttrValueEntity>().in("sku_id",skuIds));
        if (CollectionUtils.isEmpty(skuAttrValueEntities)) {
            return null;
        }
        // 以skuAttrValueEntities里面的attrId进行分组           转换成了一个流再搜集成一个map
        // 分组后的map是以attrId作为key,value是List<SkuAttrValueEntity> 也就是attrId的销售属性
        // {3:[{SkuAttrValueEntity},{SkuAttrValueEntity}] 4: 5:}
        Map<Long, List<SkuAttrValueEntity>> map = skuAttrValueEntities.stream().collect(Collectors.groupingBy(SkuAttrValueEntity::getAttrId));

        List<SaleAttrValueVo> attrValueVos = new ArrayList<>();
        map.forEach((attrId,attrValueEntities) -> {
            SaleAttrValueVo saleAttrValueVo = new SaleAttrValueVo();
            saleAttrValueVo.setAttrId(attrId);
            if (!CollectionUtils.isEmpty(attrValueEntities)) {
                // 如果销售属性集合不为空,取第一个元素获取规格参数名,例如机身颜色.....
                // 下面还要接着去规格参数一个列表(也是一个集合 如金色,白色...)
                saleAttrValueVo.setAttrName(attrValueEntities.get(0).getAttrName());
                // 不能重复 要用toSet
                // 把每个SkuAttrValueEntity中的value值搜集成一个set集合
                Set<String> attrValues = attrValueEntities.stream().map(SkuAttrValueEntity::getAttrValue).collect(Collectors.toSet());

                saleAttrValueVo.setAttrValues(attrValues);
            }
            attrValueVos.add(saleAttrValueVo);
        });

        return attrValueVos;
    }

    @Override
    public String querySaleAttrValuesMappingSkuId(Long spuId) {
        List<Map<String, Object>> maps = this.attrValueMapper.querySaleAttrValuesMappingSkuId(spuId);
        if (CollectionUtils.isEmpty(maps)) {
            return null;
        }
        Map<String, Long> collect = maps.stream().collect(Collectors.toMap(map -> map.get("attr_values").toString(), map -> (Long) map.get("sku_id")));
        return JSON.toJSONString(collect);
    }

}