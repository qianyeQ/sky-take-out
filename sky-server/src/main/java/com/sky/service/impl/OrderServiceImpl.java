package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 订单业务实现
 * <p>
 * 覆盖用户端订单的完整链路：下单、支付、历史订单、订单详情、取消订单、再来一单。
 * 说明：学习环境没有微信商户号，支付与退款都做了"模拟"处理，相关位置有注释标明。
 */
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;
    public static String URL = "https://api.map.baidu.com/geocoding/v3/?";

    /**
     * 用户下单
     * <p>
     * 整体流程：校验地址簿 → 校验购物车 → 写订单 → 写订单明细 → 清空购物车 → 返回下单结果。
     * 因为一次下单要写多张表，所以加 @Transactional，保证要么全部成功、要么全部回滚。
     *
     * @param ordersSubmitDTO 下单参数，包含地址簿id、金额、备注、餐具数量、预计送达时间等
     * @return 下单结果，其中 id 是订单主键，前端跳支付页、支付后查看订单都要用到
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 1. 校验地址簿：查不到说明地址已被删除，或者用户根本没选地址
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //检查用户的配送地址是否超出范围
        checkOutOfRange(addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());
        // 2. 查询当前用户的购物车：为空说明用户没选任何菜品，不允许下单
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list == null || list.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 3. 写入订单表：先把下单参数复制过来，再补齐订单号、状态、用户、时间等字段
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        // 订单号：学习环境用时间戳代替，真实项目一般用雪花算法等分布式id方案
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setStatus(Orders.PENDING_PAYMENT);   // 新订单默认「待付款」
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);        // 支付状态默认「未支付」
        // 收货信息从地址簿冗余一份到订单里，避免用户之后修改地址影响历史订单
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orders.setUserName(userMapper.getNameById(userId));
        LocalDateTime estimatedDeliveryTime = ordersSubmitDTO.getEstimatedDeliveryTime();
        orderMapper.insert(orders);

        // 4. 写入订单明细：购物车里的每一项对应一条明细，用 order_id 关联到刚生成的订单
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : list) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            // 订单id由数据库自增生成，insert 配置了 useGeneratedKeys，插入后会自动回填到 orders.id
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        // 一条SQL批量插入，比循环单条插入少很多次数据库交互
        orderDetailMapper.insertBatch(orderDetailList);

        // 5. 下单成功后清空购物车，避免同一批菜品被重复下单
        shoppingCartMapper.deleteByUserId(userId);

        // 6. 封装返回结果：id 必须返回，前端靠它跳转支付页和查询订单详情
        OrderSubmitVO orderSubmitVO = new OrderSubmitVO();
        orderSubmitVO.setId(orders.getId());
        orderSubmitVO.setOrderAmount(ordersSubmitDTO.getAmount());
        orderSubmitVO.setOrderNumber(orders.getNumber());
        orderSubmitVO.setOrderTime(orders.getOrderTime());
        return orderSubmitVO;
    }

    /**
     * 订单支付（学习环境为「模拟支付」）
     * <p>
     * 微信支付的正常流程是：后端调「统一下单」拿到预支付交易单 → 小程序 wx.requestPayment 拉起收银台
     * → 用户付款后微信异步回调后端，由回调去改订单状态。
     * 学习环境没有商户号、商户私钥和平台证书，这条链路走不通，因此做了三处调整：
     * 1. 不调用微信接口，直接返回一个空的支付参数对象；
     * 2. 前端去掉 wx.requestPayment，只要接口返回 code=1 就当作支付成功；
     * 3. 订单状态由 Controller 手动调用 paySuccess() 修改（等价于微信回调那一步）。
     *
     * @param ordersPaymentDTO 支付参数，实际只用到订单号 orderNumber
     * @return 支付参数对象；模拟支付时字段都为空，前端不再使用
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id（模拟支付时用不到，保留下来方便以后对接真实微信支付）
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 真实微信支付：生成预支付交易单（需要商户号、商户私钥、平台证书，学习环境未配置）
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );

        // 模拟微信的返回：空对象，后面取 code、package 拿到的都是 null
        JSONObject jsonObject = new JSONObject();

        // 保留官方逻辑：微信返回 ORDERPAID 表示该订单已经支付过，防止重复支付
        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        // 转成前端需要的 VO；模拟支付时各字段为 null，前端只判断 code 不再使用这些字段
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     * <p>
     * 真实项目中这个方法由微信支付回调（PayNotifyController）触发；
     * 学习环境跳过微信支付，由 Controller 在调完 payment() 后直接调用它。
     * 作用：把订单从「待付款」改为「待接单」，同时标记已支付、记录结账时间。
     *
     * @param outTradeNo 商户订单号，即下单时生成的 orders.number
     */
    public void paySuccess(String outTradeNo) {
        // 根据订单号查询订单（订单号传错时这里会 NPE，接入真实支付前应先做存在性校验）
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 只更新需要变化的字段：update 是动态SQL，没设置的字段不会被覆盖
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)   // 1 待付款 → 2 待接单
                .payStatus(Orders.PAID)           // 0 未支付 → 1 已支付
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 用户端历史订单分页查询
     * <p>
     * 只查当前登录用户的订单，status 为空表示查全部状态；
     * 每条订单还要再查一次菜品明细，封装成 OrderVO 返回给前端（教学项目，页大小10，可接受）。
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @param status   订单状态，为 null 表示不按状态过滤
     * @return 分页结果：总条数 + 当前页的 OrderVO 列表
     */
    @Override
    public PageResult orderQuery(Integer page, Integer pageSize, Integer status) {
        // 1. 组装查询条件：userId 限定只看自己的订单，status 为空则不拼该条件
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        Long userId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(userId);

        // 2. 分页查询：PageHelper 只对紧跟着的那一条 SQL 生效，中间不能插入其他查询
        PageHelper.startPage(page, pageSize);
        Page<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);
        // 3. 逐条查询菜品明细，把 Orders 转换成 OrderVO（N+1 查询，教学项目够用）
        List<OrderVO> orderVOList = new ArrayList<>();
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders order : ordersList) {
                OrderVO orderVO = new OrderVO();
                // OrderVO 继承自 Orders，copyProperties 会把父类字段一并复制
                BeanUtils.copyProperties(order, orderVO);
                Long orderId = order.getId();
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }
        // 4. 返回分页结果，total 取自 PageHelper 统计的总数，前端用它判断还有没有下一页
        return new PageResult(ordersList.getTotal(), orderVOList);
    }

    /**
     * 根据id查询订单详情
     * <p>
     * 分两次查询：先查订单主表，再按 order_id 查菜品明细，最后合并成 OrderVO 返回。
     * 注意：orderMapper.getById 查不到时返回 null，这里没有校验，接真实项目时应先判空。
     *
     * @param id 订单id
     * @return 订单详情（订单全部字段 + orderDetailList 菜品明细）
     */
    @Override
    public OrderVO getById(Long id) {
        // 1. 查询订单主表信息
        Orders orders = orderMapper.getById(id);
        // 2. 查询该订单下的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        // 3. 组装 VO：OrderVO 继承 Orders，copyProperties 会把订单字段一并复制过去
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 用户取消订单
     * <p>
     * 只有「待付款(1)」和「待接单(2)」的订单允许取消；
     * 待接单说明用户已经付过钱，取消时要把支付状态改成「退款」。
     * 学习环境没有商户号，真实微信退款接口调不通，因此只改支付状态，不调用退款接口。
     *
     * @param id 订单id
     */
    @Transactional
    @Override
    public void cancelById(Long id) throws Exception {
        // 1. 查订单：不存在直接抛业务异常，前端会收到"订单不存在"
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 2. 状态校验：订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        //    大于 2 说明商家已接单或已完结，用户不能再取消
        if (orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 3. 组装要更新的字段；这里用独立的 order 对象，避免把查询出来的其他字段一起写回去
        Orders order = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .cancelReason("用户取消")
                .cancelTime(LocalDateTime.now())
                .build();
        // 4. 待接单 = 用户已付款，取消时把支付状态标记为「退款」
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            // 真实场景这里要调微信退款接口（学习环境没有商户号，注释掉）：
//            weChatPayUtil.refund(
//                    orders.getNumber(), //商户订单号
//                    orders.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额
            // 模拟退款：只把支付状态改成 2（已退款）
            order.setPayStatus(Orders.REFUND);
        }
        // 5. update 是动态SQL，只会更新上面设置过的字段（状态、原因、时间、可能还有支付状态）
        orderMapper.update(order);
    }

    /**
     * 再来一单：把历史订单里的菜品重新加入购物车
     * <p>
     * 思路：按订单id查明细 → 每条明细转换成购物车对象 → 批量写入购物车。
     * 注意两点：
     * 1. 复制时要忽略明细自己的主键 id，否则购物车的主键会被明细主键污染；
     * 2. dishId 和 setmealId 都要复制过去，这样菜品和套餐才能被正确识别。
     * 前端点击"再来一单"时会先清空购物车，所以这里不需要考虑同菜品合并数量的问题。
     *
     * @param id 历史订单id
     */
    @Override
    public void repetition(Long id) {
        // 1. 查询原订单的菜品明细；查不到说明订单不存在（或明细已被删除）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 2. 逐条转换成购物车对象
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            // 复制 name、image、dishId、setmealId、dishFlavor、number、amount 等字段
            // 第三个参数 "id" 表示忽略明细主键，交给购物车表自增
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCart.setUserId(userId);
            shoppingCartList.add(shoppingCart);
        }
        // 3. 批量插入购物车，一条SQL完成
        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    /**
     * 服务端分页查询 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        //根据条件字段分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> ordersList = orderMapper.conditionSearch(ordersPageQueryDTO);
        //逐条查询菜品明细
        List<OrderVO> orderVOList = new ArrayList<>();
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders order : ordersList) {
                OrderVO orderVO = new OrderVO();
                // OrderVO 继承自 Orders，copyProperties 会把父类字段一并复制
                BeanUtils.copyProperties(order, orderVO);
                //获取订单菜品信息
                String orderDishesStr = getOrderDishesStr(order);
                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishesStr);
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(ordersList.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO Statistics() {
        // 订单状态：1待付款 2待接单 3已接单(待派送) 4派送中 5已完成 6已取消
        // 分别统计「待接单」「待派送」「派送中」三个状态的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())     // 只带 id
                .status(Orders.CONFIRMED)         // 只带 status=3
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders order = orderMapper.getById(ordersRejectionDTO.getId());
        //判断状态是否为待接单 只有待接单可以拒单
        if (order == null || !order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //设置拒单理由和状态
        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        //为用户退款
        // 真实场景这里要调微信退款接口（学习环境没有商户号，注释掉）：
//            weChatPayUtil.refund(
//                    orders.getNumber(), //商户订单号
//                    orders.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额

        // 模拟退款：只把支付状态改成 2（已退款）
        orders.setPayStatus(Orders.REFUND);
        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        //订单状态如果不为待付款、待派送、派送中、已完成状态则不能进行取消操作
        if (ordersDB == null
                || !(Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())
                || Orders.CONFIRMED.equals(ordersDB.getStatus())
                || Orders.DELIVERY_IN_PROGRESS.equals(ordersDB.getStatus())
                || Orders.COMPLETED.equals(ordersDB.getStatus()))) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //设置取消理由和状态
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        // 模拟退款：只把支付状态改成 2（已退款）
        if (ordersDB.getPayStatus() == 1) {
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
            orders.setPayStatus(Orders.REFUND);
        }
        orderMapper.update(orders);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 获取订单菜品信息
     *
     * @param order
     * @return
     */
    private String getOrderDishesStr(Orders order) {
        Long orderId = order.getId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
        StringBuilder sb = new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            sb.append(orderDetail.getName()).append("*").append(orderDetail.getNumber()).append(";");
        }
        return sb.toString();
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }
}
