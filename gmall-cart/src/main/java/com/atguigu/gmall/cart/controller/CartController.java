package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;


    @GetMapping("user/{userId}")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCartsByUserId(@PathVariable("userId")Long userId) {
       List<Cart> carts = this.cartService.queryCheckedCartsByUserId(userId);
       return ResponseVo.ok(carts);
    }


    /**
     * 添加购物车成功 重定向到购物车成功页
     * @param cart
     * @return
     */

    // 写 数据库    读  redis
    // 我们用jvm级别的异步 其实 写入数据库是出现意外失败几条也无所谓 对大数据分析来说微小批量数据不影响 反正从redis中读 对用户毫无影响

    // 所以我们用  多线程的方式完成异步  性能高 但是要保证服务器优雅的关闭
    // MQ 分布式异步,安全性上更高
    @GetMapping
    public String addCart(Cart cart) {
        if (cart == null || cart.getSkuId() == null) {
            throw new RuntimeException("没有选择添加到购物车的商品信息！");
        }
        this.cartService.addCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId();
    }  // 新增完之后跳转到addCart页面

    /**
     * 添加成功页,本质就是根据用户登录信息和skuId查询
     * @param skuId
     * @param model
     * @return
     */

    @GetMapping("addCart.html")
    public String queryCart(@RequestParam("skuId")Long skuId, Model model) {
       Cart cart = this.cartService.queryCartBySkuId(skuId);
       model.addAttribute("cart",cart);
       return "addCart";
    }

    @GetMapping("cart.html")
    public String queryCarts(Model model) {  // 不需要任何参数 完全可以根据登录信息查询(userId)未登录(userKey)
        // 但是我们需要给页面响应数据
        List<Cart> carts = this.cartService.queryCarts();
        model.addAttribute("carts",carts);
        return "cart";
    }

    // 更新数量
    @PostMapping("updateNum")
    @ResponseBody  // 异步响应json数据
    public ResponseVo updateNum(@RequestBody Cart cart) {
        this.cartService.updateNum(cart);
        return ResponseVo.ok();
    }

    // 删除商品
    @PostMapping("deleteCart")   // 获取问号后面的参数 不是占位符
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId) {
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }

    @GetMapping("test")
    public String test() {
        System.out.println(LoginInterceptor.getUserInfo());
        return "hell cart!";
    }
}
