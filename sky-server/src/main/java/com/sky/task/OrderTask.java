package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单
     */
    @Scheduled(cron = "0 * * * * ?")
//    @Scheduled(cron = "0/5 * * * * ?")
    public void processTimeOut() {
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        //获取所有状态为未支付并且超时的订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT,LocalDateTime.now().plusMinutes(-15));
        if (ordersList == null || ordersList.isEmpty()) {
            return;
        }
        //遍历超时订单并取消订单
        for (Orders orders : ordersList) {
            orderMapper.update(Orders.builder()
                    .id(orders.getId())
                    .status(Orders.CANCELLED)
                    .cancelTime(LocalDateTime.now())
                    .cancelReason("订单超时")
                    .build());
        }
    }

    /**
     * 处理一直处于派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")
//    @Scheduled(cron = "1/5 * * * * ?")
    public void processDeliveryOrder(){
        log.info("定时处理于派送中的订单：{}",LocalDateTime.now());
        //获取派送中的订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS,LocalDateTime.now().plusHours(-1));
        //判断上一个工作日还有没有派送中的订单
        if(ordersList == null || ordersList.isEmpty()){
            return;
        }
        //如果为派送中的订单则直接设置状态为已完成
        for (Orders orders : ordersList) {
            orderMapper.update(Orders.builder()
                    .id(orders.getId())
                    .status(Orders.COMPLETED)
                    .build());
        }
    }
}












