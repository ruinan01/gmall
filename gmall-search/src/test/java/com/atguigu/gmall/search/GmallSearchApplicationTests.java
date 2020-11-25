package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.api.GmallWmsApi;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;
    @Test
    void contextLoads() {

        // 初始化索引库和映射
        this.restTemplate.createIndex(Goods.class);
        this.restTemplate.putMapping(Goods.class);

        Integer pageNum = 1;
        Integer pageSize = 10;
        do {
            PageParamVo pageParamVo = new PageParamVo();
            pageParamVo.setPageNum(pageNum);
            pageParamVo.setPageSize(pageSize);
            ResponseVo<List<SpuEntity>> listResponseVo = this.pmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = listResponseVo.getData();

            // 如果恰好100的倍数 是最后一组 要判断是否为空
            if (CollectionUtils.isEmpty(spuEntities)) {
                break;
            }

            // 遍历spu, 查询spu下所有sku集合,转化成goods集合 saveAll
            spuEntities.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuEntities)) {
                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();
                        // 将sku相关信息设置进来
                        goods.setSkuId(skuEntity.getId());
                        goods.setTitle(skuEntity.getTitle());
                        goods.setSubTitle(skuEntity.getSubtitle());
                        goods.setPrice(skuEntity.getPrice().doubleValue());
                        goods.setDefaultImage(skuEntity.getDefaultImage());

                        // spu中的创建时间
                        goods.setCreateTime(spuEntity.getCreateTime());

                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuBySkuId(skuEntity.getId());
                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                            // 只要有任何一个仓库的库存减去锁定库大于0 就是有货的 大不了就调配
                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                            // 获取销量总和
                            goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a,b) -> a + b).get());

                        }

                        // 查询品牌
                        ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
                        BrandEntity brandEntity = brandEntityResponseVo.getData();
                        if (brandEntity != null) {
                            goods.setBrandId(brandEntity.getId());
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }
                        // 查询分类
                        ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(skuEntity.getCatagoryId());
                        CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                        if (categoryEntity != null) {
                            goods.setCategoryId(categoryEntity.getId());
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        // 查询规格参数
                        // 销售类型的检索规格参数及值
                        List<SearchAttrValue> searchAttrValues = new ArrayList<>();

                        ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResponseVo = this.pmsClient.querySearchSkuAttrValueBySkuIdAndCid(skuEntity.getId(), skuEntity.getCatagoryId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();

                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {

                            searchAttrValues.addAll(
                                    skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                                        BeanUtils.copyProperties(skuAttrValueEntity,searchAttrValue);
                                        return searchAttrValue;
                                    }).collect(Collectors.toList())
                            );
                        }
                        // 基本类型的检索规格参数
                        ResponseVo<List<SpuAttrValueEntity>> spuAttrValueResponseVo = this.pmsClient.querySearchSpuAttrValueBySpuIdAndCid(skuEntity.getSpuId(), skuEntity.getCatagoryId());
                        List<SpuAttrValueEntity> spuAttrValueEntities = spuAttrValueResponseVo.getData();
                        if (!CollectionUtils.isEmpty(spuAttrValueEntities)) {

                            searchAttrValues.addAll(
                                    spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                                        BeanUtils.copyProperties(spuAttrValueEntity,searchAttrValue);
                                        return searchAttrValue;
                                    }).collect(Collectors.toList())
                            );
                        }

                        goods.setSearchAttrs(searchAttrValues);

                        return goods;
                    }).collect(Collectors.toList());
                    this.goodsRepository.saveAll(goodsList);
                }
            });

            pageSize = spuEntities.size();
            pageNum++;
        } while (pageSize == 10);
    }

}
