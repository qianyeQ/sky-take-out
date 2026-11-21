package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Transactional
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO){
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        //规范化口味顺序,避免同一菜品因口味勾选顺序不同而被当成两种商品
        String dishFlavor = shoppingCart.getDishFlavor();
        if (dishFlavor != null && !dishFlavor.isEmpty()) {
            shoppingCart.setDishFlavor(Arrays.stream(dishFlavor.split("[,，]"))
                    .sorted()
                    .collect(Collectors.joining(",")));
        }
        //判断菜品或者套餐在购物车中是否存在
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list != null && !list.isEmpty()){
            //如果存在则直接将菜品或者套餐的数量在购物车中加一
            ShoppingCart cart = list.get(0);
            shoppingCartMapper.updateNumberById(cart);
        }else{
            //判断是菜品还是套餐
            if(shoppingCartDTO.getDishId() != null){
                Dish dish = dishMapper.getById(shoppingCartDTO.getDishId());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
                shoppingCart.setName(dish.getName());
            }else{
                SetmealVO setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setName(setmeal.getName());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            //如果不存在于购物车则插入数据
            shoppingCartMapper.save(shoppingCart);
        }
    }

    /**
     * 查看购物车
     * @param userId
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart(Long userId) {
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空购物车
     * @param userId
     */
    @Override
    public void cleanShoppingCart(Long userId) {
        shoppingCartMapper.deleteByUserId(userId);
    }
}
