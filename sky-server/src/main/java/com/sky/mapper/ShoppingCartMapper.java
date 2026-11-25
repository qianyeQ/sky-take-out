package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 动态条件查询
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 购物车商品数量加一
     * @param shoppingCart
     */
    @Update("update shopping_cart set number = number + 1 where id = #{id}")
    void updateNumberById(ShoppingCart shoppingCart);

    /**
     * 购物车商品数量减一
     * @param id 购物车记录id
     */
    @Update("update shopping_cart set number = number - 1 where id = #{id}")
    void decreaseNumberById(Long id);

    /**
     * 根据id删除购物车中的一条记录（数量减到0时使用）
     * @param id 购物车记录id
     */
    @Delete("delete from shopping_cart where id = #{id}")
    void deleteById(Long id);

    /**
     * 为购物车插入数据
     * @param shoppingCart
     */
    void save(ShoppingCart shoppingCart);

    /**
     * 根据用户id查询购物车数据
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    void insertBatch(List<ShoppingCart> shoppingCartList);
}
