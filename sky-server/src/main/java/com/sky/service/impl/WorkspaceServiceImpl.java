package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作台业务实现
 * <p>
 * 说明：本项目统计相关的 Mapper 方法命名和老师版本不同，这里统一改用：
 * orderMapper.getOrderCountByTime(map)  按条件统计订单数量
 * orderMapper.getTurnOverByTime(...)    按时间段统计营业额
 * userMapper.getNewUserCountByTime(...) 按时间段统计新增用户
 */
@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 根据时间段统计营业数据
     * <p>
     * 营业额 = 已完成订单总金额；有效订单数 = 已完成订单数量；
     * 订单完成率 = 有效订单数 / 总订单数；平均客单价 = 营业额 / 有效订单数；
     * 新增用户 = 时间段内注册的用户数。
     *
     * @param begin 开始时间
     * @param end   结束时间
     * @return 营业数据
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        Map<String, Object> map = new HashMap<>();
        map.put("begin", begin);
        map.put("end", end);

        // 1. 总订单数（不限制状态）
        Integer totalOrderCount = orderMapper.getOrderCountByTime(map);

        // 2. 营业额（只统计已完成订单，SQL 里用了 ifnull，正常不会为 null）
        Double turnover = orderMapper.getTurnOverByTime(Orders.COMPLETED, begin, end);
        turnover = turnover == null ? 0.0 : turnover;

        // 3. 有效订单数（已完成订单）
        map.put("status", Orders.COMPLETED);
        Integer validOrderCount = orderMapper.getOrderCountByTime(map);

        // 4. 订单完成率、平均客单价（都要防除零）
        double orderCompletionRate = 0.0;
        double unitPrice = 0.0;
        if (totalOrderCount != null && totalOrderCount != 0
                && validOrderCount != null && validOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            unitPrice = turnover / validOrderCount;
        }

        // 5. 新增用户数
        Integer newUsers = userMapper.getNewUserCountByTime(begin, end);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    /**
     * 查询订单管理数据：今天各状态订单数量
     *
     * @return 订单总览
     */
    @Override
    public OrderOverViewVO getOrderOverView() {
        Map<String, Object> map = new HashMap<>();
        // 只统计今天：begin = 今天 00:00:00，不传 end 表示不限结束时间
        map.put("begin", LocalDateTime.now().with(LocalTime.MIN));

        // 待接单
        map.put("status", Orders.TO_BE_CONFIRMED);
        Integer waitingOrders = orderMapper.getOrderCountByTime(map);

        // 待派送（已接单）
        map.put("status", Orders.CONFIRMED);
        Integer deliveredOrders = orderMapper.getOrderCountByTime(map);

        // 已完成
        map.put("status", Orders.COMPLETED);
        Integer completedOrders = orderMapper.getOrderCountByTime(map);

        // 已取消
        map.put("status", Orders.CANCELLED);
        Integer cancelledOrders = orderMapper.getOrderCountByTime(map);

        // 全部订单：不传 status，动态 SQL 会忽略该条件
        map.remove("status");
        Integer allOrders = orderMapper.getOrderCountByTime(map);

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览：起售、停售数量
     *
     * @return 菜品总览
     */
    @Override
    public DishOverViewVO getDishOverView() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", StatusConstant.ENABLE);
        Integer sold = dishMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = dishMapper.countByMap(map);

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    /**
     * 查询套餐总览：起售、停售数量
     *
     * @return 套餐总览
     */
    @Override
    public SetmealOverViewVO getSetmealOverView() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", StatusConstant.ENABLE);
        Integer sold = setmealMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = setmealMapper.countByMap(map);

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
