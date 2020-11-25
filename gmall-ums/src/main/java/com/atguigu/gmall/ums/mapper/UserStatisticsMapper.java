package com.atguigu.gmall.ums.mapper;

import com.atguigu.gmall.ums.entity.UserStatisticsEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统计信息表
 * 
 * @author liruinan
 * @email liruinan@atguigu.com
 * @date 2020-10-28 08:58:44
 */
@Mapper
public interface UserStatisticsMapper extends BaseMapper<UserStatisticsEntity> {
	
}
