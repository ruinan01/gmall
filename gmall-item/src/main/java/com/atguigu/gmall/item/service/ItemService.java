package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVo load(Long skuId) {
        ItemVo itemVo = new ItemVo();

        // 1 根据skuId查询sku的信息
        CompletableFuture<SkuEntity> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new RuntimeException("商品信息不存在");
            }
            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(skuEntity.getWeight());
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            return skuEntity;
        },threadPoolExecutor);
        
        // 2 根据sku中分类id查询三级分类
        CompletableFuture<Void> catesCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<CategoryEntity>> categoryResponseVo = this.pmsClient.queryAllLvlCategoriesByCid3(skuEntity.getCatagoryId());
            List<CategoryEntity> categoryEntities = categoryResponseVo.getData();
            itemVo.setCategories(categoryEntities);
        }, threadPoolExecutor);

        // 3 根据sku中品牌id查询品牌
        CompletableFuture<Void> brandCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());  // 有点操作 可能有空指针异常 所有要判空
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        // 4 根据sku中spuId查询spu
        CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());

            }
        }, threadPoolExecutor);

        // 5 根据skuId查询图片
        CompletableFuture<Void> imageCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuImagesEntity>> imagesResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = imagesResponseVo.getData();
            itemVo.setImages(skuImagesEntities);
        }, threadPoolExecutor);

        // 6 根据skuId查询sku的营销信息
        CompletableFuture<Void> salesCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            itemVo.setSales(itemSaleVos);
        }, threadPoolExecutor);

        // 7 根据skuId查询库存信息
        CompletableFuture<Void> wareCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkuBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity ->
                        wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, threadPoolExecutor);

        // 8 根据spuId查询spu下所有sku的销售属性
        CompletableFuture<Void> saleAttrsCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SaleAttrValueVo>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValuesBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrValueVos = saleAttrsResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrValueVos);
        }, threadPoolExecutor);


        // 9 根据skuId查询当前sku的销售属性
        CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrsBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                Map<Long, String> map = skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue));
                itemVo.setSaleAttr(map);
            }
        }, threadPoolExecutor);

        // 10 根据skuId查询spu下所有sku和销售属性组合的映射关系
        CompletableFuture<Void> mappingCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<String> stringResponseVo = this.pmsClient.querySaleAttrValuesMappingSkuId(skuEntity.getSpuId());
            String json = stringResponseVo.getData();
            itemVo.setSkusJson(json);
        }, threadPoolExecutor);

        // 11 根据spuId查询商品描述信息
        CompletableFuture<Void> descCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null) {
                itemVo.setSpuImages(Arrays.asList(StringUtils.split(spuDescEntity.getDecript(), ",")));
            }
        }, threadPoolExecutor);

        // 12 根据cid,skuId,spuId查询组及组下的规格参数
        CompletableFuture<Void> groupCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<GroupVo>> groupResponseVo = this.pmsClient.queryGroupWithAttrValuesByCidAndSpuIdAndSkuId(skuEntity.getCatagoryId(), skuId, skuEntity.getSpuId());
            List<GroupVo> groupVos = groupResponseVo.getData();
            itemVo.setGroups(groupVos);
        }, threadPoolExecutor);

        CompletableFuture.allOf(catesCompletableFuture,brandCompletableFuture,spuCompletableFuture,
                imageCompletableFuture,salesCompletableFuture,wareCompletableFuture,
                saleAttrsCompletableFuture,saleAttrCompletableFuture,mappingCompletableFuture,
                descCompletableFuture,groupCompletableFuture).join();
        return itemVo;
    }

}
class ThreadTest {
    public static void main(String[] args) throws IOException {

//        CompletableFuture.runAsync(() -> {
//            System.out.println("通过CompletableFuture的runAsync方法初始化一个子任务!");
//        });
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            // int i = 10 / 0;
            return "hello supplyAsync";
        });
        CompletableFuture<String> future1 = future.thenApplyAsync(t -> {
            System.out.println("==========thenApplyAsync 1==========");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t: " + t);
            return "hello thenApplyAsync";
        });
        CompletableFuture<Void> future2 = future.thenAcceptAsync(t -> {
            System.out.println("==========thenAcceptAsync 2==========");
            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t: " + t);
        });
        CompletableFuture<Void> future3 = future.thenRunAsync(() -> {
            System.out.println("==========thenRunAsync 3==========");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("这个任务是一个thenRun任务,没有返回结果集");
        });
        // 不放future 是因为 1,2,3执行的时候它已经执行完了所以之要等123执行完即可
        CompletableFuture.allOf(future1,future2,future3).join();
        System.out.println("主线程返回....");

//                .whenCompleteAsync((t,u) -> {  // 不会阻塞主线程的
//            System.out.println("-----------whenCompleteAsync---------");
//            System.out.println("t: " + t); // t 是上一个任务的返回信息 xxx
//            System.out.println("u: " + u); // u 是上一个任务的异常信息
//        }).exceptionally(t -> {
//            System.out.println("-----------exceptionally---------");
//            System.out.println("t: " + t);
//            return "hello exceptionally";
//        });
//
//        System.out.println("123456");

        System.in.read();

    }
}
