package com.atguigu.gmall.search.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;
import java.util.List;

@Data
@Document(indexName = "goods", type = "info", shards = 3, replicas = 2)
public class Goods {

    // 搜索列表字段
    @Id
    private Long skuId;
    @Field(type = FieldType.Text,analyzer = "ik_max_word")
    private String title;
    @Field(type = FieldType.Keyword,index = false)  // index 默认是true 创建搜索
    private String defaultImage;
    @Field(type = FieldType.Double)
    private Double price;
    @Field(type = FieldType.Keyword,index = false)  //Keyword 不分词 false 不创建搜索
    private String subTitle;

    // 排序和筛选字段 (销量,新品,是否有货)
    @Field(type = FieldType.Long)    // index 默认是true 创建搜索
    private Long sales = 0L; // 销量
    @Field(type = FieldType.Date)
    private Date createTime;  // 新品查询
    @Field(type = FieldType.Boolean)
    private Boolean store = false; // 是否有货(库存信息)


    // 聚合字段 (id,名称,logo)
    // 品牌所需字段
    @Field(type = FieldType.Long)    // index 默认是true 创建搜索 进行聚合
    private Long brandId;  // 聚合品牌id传过去
    @Field(type = FieldType.Keyword)
    private String brandName; // 鼠标放上去显示的品牌名称聚合
    @Field(type = FieldType.Keyword)
    private String logo;  // logo图片聚合

    // 分类所需字段
    @Field(type = FieldType.Long)
    private Long categoryId;  // 分类Id
    @Field(type = FieldType.Keyword)
    private String categoryName;   // 分类名称

    @Field(type = FieldType.Nested)  // 嵌套字段  如果不设置 就会出现数据扁平化
    // 集合中存放 规格参数名称 Id  一个规格参数(     例如屏幕尺寸(5  6.4  6.9 也是聚合出来的)    )
    private List<SearchAttrValue> searchAttrs;  // 规格参数(京东<规格参数Id_参数值Id%E.../单独放在了一个子表中>)

}
