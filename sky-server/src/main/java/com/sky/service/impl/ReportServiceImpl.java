package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

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
}
