package com.atguigu.gmall.ums.mapper;

import com.atguigu.gmall.ums.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表
 * 
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-28 08:58:44
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
	
}
