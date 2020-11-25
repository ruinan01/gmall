package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.vo.GroupVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;

import java.util.List;
import java.util.Map;

/**
 * 属性分组
 *
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-27 20:47:15
 */
public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<AttrGroupEntity> queryGroupWithAttrsByCid(Long cid);

    List<GroupVo> queryGroupWithAttrValuesByCidAndSpuIdAndSkuId(Long cid, Long skuId, Long spuId);
}

