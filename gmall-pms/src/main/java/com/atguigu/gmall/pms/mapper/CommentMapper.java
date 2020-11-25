package com.atguigu.gmall.pms.mapper;

import com.atguigu.gmall.pms.entity.CommentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品评价
 * 
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-27 20:47:15
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {
	
}
