package com.atguigu.gmall.search.pojo;

import lombok.Data;

import java.util.List;

/**
 * search.gmall.com/search?keyword=小米
 * &brandId=1,3&cid=225&props=5:高通-麒麟
 * &props=6:骁龙865-硅谷1代&sort=1
 * &priceFrom=1000&priceTo=6000&pageNum=1&store=true
 */

@Data
public class SearchParamVo {

    private String keyword;  // 检索条件

    private List<Long> brandId;  // 品牌过滤

    private List<Long> categoryId;  // 分类过滤

    // props=5:高通-麒麟,6:骁龙865-硅谷1代
    private List<String> props; // 过滤的检索参数

    // 排序字段 0:默认得分降序 1:价格升序  2:价格降序  3:新品降序  4:销量降序
    private Integer sort = 0;

    // 价格区间
    private Double priceFrom;
    private Double priceTo;

    private Integer pageNum = 1; //页码
    private final Integer pageSize = 20; // 每页记录数

    // 是否有货
    private Boolean store;
}
