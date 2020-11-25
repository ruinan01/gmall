package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(Long cid, PageParamVo pageParamVo) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        if (cid != 0) {
            wrapper.eq("category_id", cid);
        }

        // 判断查询条件 是否为空或空格
        String key = pageParamVo.getKey();
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(t -> t.eq("id",key).or().like("name",key));
        }

        IPage<SpuEntity> page = this.page(
                // 转换成IPage对象 mp就可以进行分页查询 查出来又是一个IPage对象
                // 不是页面需要的结果集 需要的是PageResultVo(page)结果集 提供了一个构造方法 所以可以成功
                pageParamVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @Autowired
//    private SpuDescMapper descMapper;
    private SpuDescService descService;

    @Autowired
    private SpuAttrValueService attrValueService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuImagesService imagesService;

    @Autowired
    private SkuAttrValueService skuAttrValueService;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @GlobalTransactional
    public void bigSave(SpuVo spu) {
        // 1 保存spu相关信息
        // 1.1 保存spu表
        Long spuId = saveSpu(spu);

        // 1.2 保存spu_desc表
        // saveSpuDesc(spu,spuId); // 会
        this.descService.saveSpuDesc(spu,spuId);

        // 1.3 保存spu_attr_value表
        saveBaseAttr(spu,spuId);

        // 2 保存sku相关信息
        saveSkus(spu,spuId);

        this.rabbitTemplate.convertAndSend("PMS_SPU_EXCHANGE","item.insert",spuId);

       // int i = 1 / 0;
    }

    private void saveSkus(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }

        skus.forEach(skuVo -> {
            // 2.1 保存sku表
            skuVo.setSpuId(spuId);
            // 品牌和分类的id需要从spuInfo种获取
            skuVo.setBrandId(spu.getBrandId());
            skuVo.setCatagoryId(spu.getCategoryId());
            // 获取图片列表
            List<String> images = skuVo.getImages();
            // 如果图片列表不为 null ,则设置默认图片
            if (!CollectionUtils.isEmpty(images)) {
                // 设置如果用户没有传入第一张图片作为默认图片 有则使用
                skuVo.setDefaultImage(StringUtils.isNotBlank(skuVo.getDefaultImage()) ? skuVo.getDefaultImage() : images.get(0));
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();
            // 2.2 保存sku的图片表
            if (!CollectionUtils.isEmpty(images)) {
                List<SkuImagesEntity> skuImagesEntities = images.stream().map(
                        image -> {
                            SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                            skuImagesEntity.setSkuId(skuId);
                            skuImagesEntity.setUrl(image);
                            skuImagesEntity.setDefaultStatus(StringUtils.equals(image,skuVo.getDefaultImage()) ? 1 : 0);
                            return skuImagesEntity;
                        }
                ).collect(Collectors.toList());
                this.imagesService.saveBatch(skuImagesEntities);
            }

            // 2.3 保存销售属性表(sku_attr_value)
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)) {
                saleAttrs.forEach(saleAttr -> saleAttr.setSkuId(skuId));
                this.skuAttrValueService.saveBatch(saleAttrs);
            }


            // 3 保存sku的营销信息
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo,skuSaleVo);
            skuSaleVo.setSkuId(skuVo.getId());
            this.smsClient.saveSales(skuSaleVo);
        });
    }

    private void saveBaseAttr(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<SpuAttrValueEntity> spuAttrValueEntities =
                    baseAttrs.stream().map(spuAttrValueVo -> {
                        // map 可以把一个集合转换成另一个集合
                        // 本来是SpuAttrValueVo 转换成了 SpuAttrValueEntity 存给了spuAttrValueEntities
                        SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                        BeanUtils.copyProperties(spuAttrValueVo,spuAttrValueEntity);
                        spuAttrValueEntity.setSpuId(spuId);
                        return spuAttrValueEntity;
                    }).collect(Collectors.toList());
            this.attrValueService.saveBatch(spuAttrValueEntities);
        }
    }



    private Long saveSpu(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
        return spu.getId();
    }

}