package com.atguigu.gmall.scheduled.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("cart_info")  // 表名和实体类名称不同 所以加个TableName注解指定以下
public class Cart {

    @TableId
    private Long id;  // 主键
    @TableField("user_id")  // 和数据库字段不能匹配 所以加TableField注解标识
    private String userId;
    @TableField("sku_id")
    private Long skuId;
    @TableField("`check`") // check是mysql的关键字，所以这里要加'`'号
    private Boolean check; // 选中状态
    private String defaultImage;
    private String title;
    @TableField("sale_attrs")
    private String saleAttrs; // 销售属性：List<SkuAttrValueEntity>的json格式
    private BigDecimal price; // 加入购物车时的价格
    @TableField(exist = false)
    private BigDecimal currentPrice; // 实时价格
    private BigDecimal count;
    private Boolean store = false; // 是否有货
    private String sales; // 营销信息: List<ItemSaleVo>的json格式
}