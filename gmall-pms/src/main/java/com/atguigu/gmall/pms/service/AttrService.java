package com.atguigu.gmall.pms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.pms.entity.AttrEntity;

import java.util.List;
import java.util.Map;

/**
 * 商品属性
 *
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-27 20:47:15
 */
public interface AttrService extends IService<AttrEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    List<AttrEntity> queryAttrsByGid(Long gid);

    List<AttrEntity> queryAttrsByCidAndTypeOrSearchType(Long cid, Integer type, Integer searchType);
}

