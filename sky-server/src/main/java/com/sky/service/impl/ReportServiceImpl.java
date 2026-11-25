package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.xslf.usermodel.XSLFSheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
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
    @Autowired
    private WorkspaceService workspaceService;

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
            Double turnover = orderMapper.getTurnOverByTime(Orders.COMPLETED, beginTime, endTime);
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
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从开始到结束范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)) {
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
            Integer newUserCount = userMapper.getNewUserCountByTime(beginTime, endTime);
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
        while (!temp.equals(end)) {
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
            Integer totalOrderCount = getOrderCount(null, beginTime, endTime);
            totalOrderCountList.add(totalOrderCount);
            //获取有效订单总数
            Integer validOrderCount = getOrderCount(Orders.COMPLETED, beginTime, endTime);
            validOrderCountList.add(validOrderCount);
        }
        //获取范围时间内总订单数
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        Integer totalOrderCount = getOrderCount(null, beginTime, endTime);
        //获取范围时间内有效订单数
        Integer validOrderCount = getOrderCount(Orders.COMPLETED, beginTime, endTime);
        //计算订单完成率
        double orderCompletionRate = totalOrderCount == 0 ? 0.0 : ((double) validOrderCount / totalOrderCount);
        //封装数据返回
        return OrderReportVO.builder()
                .dateList(dateList.stream().map(date -> date.toString()).collect(Collectors.joining(",")))
                .orderCountList(StringUtils.join(totalOrderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .validOrderCount(validOrderCount)
                .totalOrderCount(totalOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计指定时间区间内菜品销量top10数据
     *
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
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();
    }

    /**
     * 导出运营数据报表
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) throws IOException {
        //查询数据库，获取营业数据--查询最近30天
        LocalDate dateBegin = LocalDate.now().plusDays(-30);
        LocalDate dateEnd = LocalDate.now().plusDays(-1);
        //查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));
        //通过POI把数据写到Excel文件中
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        //基于模板文件创建一个Excel对象
        XSSFWorkbook excels = new XSSFWorkbook(is);
        //获取表格文件的标签页
        XSSFSheet sheet1 = excels.getSheetAt(0);
        //填充时间
        XSSFRow row = sheet1.getRow(1);
        row.getCell(1).setCellValue("时间：" + dateBegin + "至" + dateEnd);
        //填充营业数据
        row = sheet1.getRow(3);
        row.getCell(2).setCellValue(businessDataVO.getTurnover());
        row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
        row.getCell(6).setCellValue(businessDataVO.getNewUsers());

        row = sheet1.getRow(4);
        row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
        row.getCell(4).setCellValue(businessDataVO.getUnitPrice());
        //填充明细数据
        for(int i = 0;i < 30;i++){
            //查询某一天的营业数据
            LocalDate date = dateBegin.plusDays(i);
            BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
            //获得某一行
            row = sheet1.getRow(7 + i);
            row.getCell(1).setCellValue(date.toString());
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(3).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(5).setCellValue(businessData.getUnitPrice());
            row.getCell(6).setCellValue(businessData.getNewUsers());
        }
        //通过输出流把Excel文件下载到客户端浏览器
        ServletOutputStream out = response.getOutputStream();
        excels.write(out);
        out.close();
        excels.close();
    }

    private Integer getOrderCount(Integer status, LocalDateTime beginTime, LocalDateTime endTime) {
        Map<String, Object> map = new HashMap();
        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status", status);
        Integer OrderCount = orderMapper.getOrderCountByTime(map);
        return OrderCount;
    }
}
