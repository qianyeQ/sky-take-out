package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看购物车
     * @param userId
     * @return
     */
    List<ShoppingCart> showShoppingCart(Long userId);

    /**
     * 清空购物车
     * @param userId
     */
    void cleanShoppingCart(Long userId);

    /**
     * 减少购物车中一个商品（数量减一，数量为1时删除该记录）
     * @param shoppingCartDTO 商品信息
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);
}
