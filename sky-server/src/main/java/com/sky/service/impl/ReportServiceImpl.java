package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定时间区间内营业额数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从开始到结束范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
            //日期计算，计算指定日期的后一天的对应日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //遍历集合用逗号分隔转成字符串
        String resultDateList = dateList.stream().map(date -> date.toString()).collect(Collectors.joining(","));
        //遍历日期集合获取每一天完成订单的营业额
        StringJoiner resultTurnoverList = new StringJoiner(",");
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Double turnover = orderMapper.getTurnOverByTime(Orders.COMPLETED,beginTime,endTime);
            resultTurnoverList.add(turnover.toString());
        }
        //把结果封装到对象
        return TurnoverReportVO.builder()
                .dateList(resultDateList)
                .turnoverList(resultTurnoverList.toString())
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从开始到结束范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            //日期计算，计算指定日期的后一天的对应日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //遍历集合获取每天新增的用户数量以及用户总数
        List<Integer> userCountList = new ArrayList<>();
        List<Integer> newUserCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer userCount = userMapper.getUserCountByTime(endTime);
            Integer newUserCount = userMapper.getNewUserCountByTime(beginTime,endTime);
            userCountList.add(userCount);
            newUserCountList.add(newUserCount);
        }
        //把结果封装到对象
        return (UserReportVO.builder()
                .dateList(dateList.stream().map(date -> date.toString()).collect(Collectors.joining(",")))
                .newUserList(newUserCountList.stream().map(newUser -> newUser.toString()).collect(Collectors.joining(",")))
                .totalUserList(userCountList.stream().map(totalUser -> totalUser.toString()).collect(Collectors.joining(",")))
                .build());
    }

    /**
     * 订单统计
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从开始到结束范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        LocalDate temp = begin;
        while(!temp.equals(end)){
            //日期计算，计算指定日期的后一天的对应日期
            temp = temp.plusDays(1);
            dateList.add(temp);
        }
        //遍历集合获取每天的订单数量以及有效订单数量
        List<Integer> totalOrderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //获取订单总数
            Integer totalOrderCount = getOrderCount(null,beginTime,endTime);
            totalOrderCountList.add(totalOrderCount);
            //获取有效订单总数
            Integer validOrderCount = getOrderCount(Orders.COMPLETED,beginTime,endTime);
            validOrderCountList.add(validOrderCount);
        }
        //获取范围时间内总订单数
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        Integer totalOrderCount = getOrderCount(null,beginTime,endTime);
        //获取范围时间内有效订单数
        Integer validOrderCount = getOrderCount(Orders.COMPLETED,beginTime,endTime);
        //计算订单完成率
        double orderCompletionRate = totalOrderCount == 0 ? 0.0 :((double) validOrderCount / totalOrderCount);
        //封装数据返回
        return OrderReportVO.builder()
                .dateList(dateList.stream().map(date -> date.toString()).collect(Collectors.joining(",")))
                .orderCountList(StringUtils.join(totalOrderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .validOrderCount(validOrderCount)
                .totalOrderCount(totalOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计指定时间区间内菜品销量top10数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> SalesTop10List = orderMapper.getNameByTime(Orders.COMPLETED, beginTime, endTime);
        List<String> nameList = new ArrayList<>();
        List<Integer> numberList = new ArrayList<>();
        for (GoodsSalesDTO goodsSalesDTO : SalesTop10List) {
            nameList.add(goodsSalesDTO.getName());
            numberList.add(goodsSalesDTO.getNumber());
        }
        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList,","))
                .numberList(StringUtils.join(numberList,","))
                .build();
    }

    private Integer getOrderCount(Integer status,LocalDateTime beginTime,LocalDateTime endTime){
        Map<String,Object> map = new HashMap();
        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status",status);
        Integer OrderCount = orderMapper.getOrderCountByTime(map);
        return OrderCount;
    }
}
