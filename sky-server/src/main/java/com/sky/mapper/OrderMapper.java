package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 分页查询订单信息
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询详细订单信息
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据指定条件搜索订单信息
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据订单状态统计订单数量
     * @param status 订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
     * @return 该状态下的订单数量
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     *  查询支付超时订单
     * @param status
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status,LocalDateTime orderTime);

    @Select("select ifnull(sum(amount),0) from orders where status = #{status} and order_time between #{begin} and #{end}")
    Double getTurnOverByTime(Integer status,LocalDateTime begin,LocalDateTime end);

    Integer getOrderCountByTime(Map<String,Object> map);
}
