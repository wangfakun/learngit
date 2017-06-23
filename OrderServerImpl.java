package com.jlpay.rrd.appserver.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jlpay.rrd.appserver.dao.AttestMapper;
import com.jlpay.rrd.appserver.dao.InstallRuleMapper;
import com.jlpay.rrd.appserver.dao.ManagerSaleRelMapper;
import com.jlpay.rrd.appserver.dao.OrderInfoMapper;
import com.jlpay.rrd.appserver.dao.OrderProcessMapper;
import com.jlpay.rrd.appserver.entity.Attest;
import com.jlpay.rrd.appserver.entity.InstallRule;
import com.jlpay.rrd.appserver.entity.OrderDesc;
import com.jlpay.rrd.appserver.entity.OrderInfo;
import com.jlpay.rrd.appserver.entity.OrderProcessInfo;
import com.jlpay.rrd.appserver.entity.SequenceInfo;
import com.jlpay.rrd.appserver.exception.ServiceException;
import com.jlpay.rrd.appserver.service.IOrderServer;
import com.jlpay.rrd.appserver.util.Constants;
import com.jlpay.rrd.appserver.util.ImageUtil;
import com.jlpay.rrd.appserver.util.UnitConvertUtil;
import com.jlpay.rrd.common.util.DateUtil;
import com.jlpay.rrd.common.util.StringUtils;

/**
 * 进件
 * 
 * @author yinhaijuan
 * 
 */
@Service
public class OrderServerImpl implements IOrderServer {

	@Autowired
	private OrderInfoMapper orderInfoMapper;
	@Autowired
	private OrderProcessMapper orderProcessMapper;
	@Autowired
	private ManagerSaleRelMapper managerSaleRelMapper;
	@Autowired
	private InstallRuleMapper installRuleMapper;
	@Autowired
	private AttestMapper attestMapper;

	/**
	 * 进件申请
	 * 
	 * @param order
	 * @return
	 * @throws Throwable
	 */
	@Transactional(rollbackFor = Throwable.class)
	public Integer addOrderInfo(OrderInfo order) throws ServiceException {
		SequenceInfo sequence = new SequenceInfo();
		orderInfoMapper.getSequenceId(sequence);
		String orderId = Constants.ORDER_PREFIX + String.format("%010d", sequence.getId());
		order.setOrderId(orderId);
		orderInfoMapper.save(order);
		OrderProcessInfo orderProcess = new OrderProcessInfo();
		orderProcess.setTrackId(order.getId());
		orderProcess.setStatus(Constants.ORDER_STATUS_COMMIT);
		orderProcess.setOperateTime(new Date());
		orderProcess.setOperateId(order.getManagerId());
		orderProcessMapper.save(orderProcess);
		// 进件申请成功后推送消息给业务员
		// sendAppMessage();
		return order.getId();
	}

	/**
	 * 进件申请成功后推送消息给业务员
	 */
	// private void sendAppMessage() {
	// executor.execute(new Runnable() {
	//
	// @Override
	// public void run() {
	//
	// try {
	//
	// } catch (Exception e) {
	//
	// }
	// }
	// });
	// }

	/**
	 * 查询客户经理关联的业务员Id
	 * 
	 * @param managerId
	 * @return
	 */
	public Integer querySalesId(int managerId) {
		Integer salesId = managerSaleRelMapper.querySalesId(managerId);
		if (null == salesId) {
			return null;
		}
		return salesId;
	}

	/**
	 * 客户经理查询我的进件
	 * 
	 * @param managerId
	 * @return
	 */
	public Map<String, Object> queryManagerOrdersByPage(String managerId, String orderStatus, int curPage, int pageSize) {
		Map<String, Object> countMap = new HashMap<String, Object>();
		countMap.put("managerId", Integer.parseInt(managerId));
		countMap.put("orderStatus", Integer.parseInt(orderStatus));
		int total = orderInfoMapper.queryManagerOrderCount(countMap);
		// 没有真实订单则查询示例订单
		if (total == 0) {
			if (Constants.ORDER_STATUS_FINISH == Integer.parseInt(orderStatus)) {
				countMap.put("orderStatus", 0);
				int total2 = orderInfoMapper.queryManagerOrderCount(countMap);
				if (total2 > 0) {
					Map<String, Object> pageMap = new HashMap<String, Object>();
					pageMap.put("curPage", curPage);
					pageMap.put("total", total);
					Map<String, Object> resultMap = new HashMap<String, Object>();
					resultMap.put("page", pageMap);
					resultMap.put("code", "0000");
					resultMap.put("desc", "没有查到进件信息");
					resultMap.put("data", null);
					return resultMap;
				}
			}
			// 没有认证，则不显示示例商户
			Attest attest1 = attestMapper.selectAttestByManagerId(Integer.parseInt(managerId));
			if (null == attest1
					|| (Constants.ORDER_STATUS_FINISH != Integer.parseInt(orderStatus) && 0 != Integer
							.parseInt(orderStatus))) {
				Map<String, Object> pageMap = new HashMap<String, Object>();
				pageMap.put("curPage", curPage);
				pageMap.put("total", total);
				Map<String, Object> resultMap = new HashMap<String, Object>();
				resultMap.put("page", pageMap);
				resultMap.put("code", "0000");
				resultMap.put("desc", "没有查到进件信息");
				resultMap.put("data", null);
				return resultMap;
			}
			OrderInfo order = convertExamOrder(managerId);
			List<OrderInfo> lstOrder = new ArrayList<OrderInfo>(1);
			lstOrder.add(order);
			Map<String, Object> resultMap = new HashMap<String, Object>();
			Map<String, Object> pageMap = new HashMap<String, Object>();
			pageMap.put("curPage", curPage);
			pageMap.put("total", 1);
			resultMap.put("page", pageMap);
			resultMap.put("code", "0000");
			resultMap.put("desc", "成功");
			resultMap.put("data", lstOrder);
			return resultMap;
		}
		int totalPage = total % pageSize == 0 ? total / pageSize : total / pageSize + 1;
		if (curPage > totalPage) {
			curPage = totalPage;
		}
		int startRow = (curPage - 1) * pageSize;
		Map<String, Object> paramMap = new HashMap<String, Object>();
		paramMap.put("managerId", Integer.parseInt(managerId));
		paramMap.put("orderStatus", Integer.parseInt(orderStatus));
		paramMap.put("startRow", startRow);
		paramMap.put("maxSize", pageSize);
		List<OrderInfo> lstOrder = orderInfoMapper.queryManagerOrders(paramMap);
		if (null != lstOrder) {
			for (OrderInfo order : lstOrder) {
				convertUnitAndDesc(order);
			}
		}
		Map<String, Object> pageMap = new HashMap<String, Object>();
		pageMap.put("curPage", curPage);
		pageMap.put("total", total);
		Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap.put("desc", "成功");
		resultMap.put("data", lstOrder);
		return resultMap;
	}

	/**
	 * 查询进件详情
	 * 
	 * @param orderId
	 * @return
	 */
	public OrderInfo queryOrderDetail(String id) {
		OrderInfo order = null;
		if ("-200".equals(id)) {
			order = convertExampleOrder();
		} else {
			order = orderInfoMapper.queryOrderDetail(Integer.parseInt(id));
		}
		if (null == order) {
			return null;
		}
		convertUnitAndDesc(order);
		convertImageUrl(order);
		return order;
	}

	/**
	 * 组装示例订单
	 * @return
	 */
	private OrderInfo convertExamOrder(String managerId) {
		OrderInfo order = new OrderInfo();
		order.setId(-200);
		// 示例订单id以200开头
		order.setOrderId("2000000000001");
		order.setManagerId(Integer.parseInt(managerId));
		order.setContactName("易装机示例订单");
		order.setContactPhone("13800138000");
		order.setMerchName("易装机示例商户");
		order.setMachineAddress("广东省深圳市");
		order.setFeeRate(Float.valueOf("0.78"));
		order.setMachineNumber(2);
		order.setPrice(45000f);
		order.setProvideType("1");
		order.setSalesId(-1);
		order.setOrderStatus(Constants.ORDER_STATUS_FINISH);
		Date operateTime = DateUtil.strToDate("2016-02-01 09:00:00");
		order.setCreateTime(operateTime);
		order.setLastUpdateTime(operateTime);
		return order;
	}

	/**
	 * 组装示例订单
	 * @return
	 */
	private OrderInfo convertExampleOrder() {
		OrderInfo order = convertExamOrder("-1");
		List<OrderProcessInfo> lstProcess = new ArrayList<OrderProcessInfo>();
		for (OrderDesc ord : OrderDesc.values()) {
			if (ord.getStatus() == Constants.ORDER_STATUS_ABORT) {
				continue;
			}
			OrderProcessInfo orderProcess = new OrderProcessInfo();
			orderProcess.setOperateId(-1);
			orderProcess.setProcessId(ord.getStatus());
			orderProcess.setTrackId(order.getId());
			orderProcess.setStatus(ord.getStatus());
                        give me a line 
			Calendar afterTime = Calendar.getInstance();
			afterTime.setTime(order.getCreateTime());
			afterTime.add(Calendar.MINUTE, ord.getStatus());
			orderProcess.setOperateTime(afterTime.getTime());
			orderProcess.setOperateName("小易");
			lstProcess.add(orderProcess);
		}
		order.setOrderProcess(lstProcess);
		return order;
	}

	@Transactional(rollbackFor = Throwable.class)
	public void updateOrder(OrderInfo order) throws ServiceException {
		orderInfoMapper.updateOrder(order);
	}

	@Override
	public InstallRule selectRule() {
		return installRuleMapper.selectRule();
	}

	/**
	 * 查询当月的进件数
	 * 
	 * @param paramMap
	 * @return
	 */
	public List<OrderInfo> queryOrdersByMonth(Map<String, Object> paramMap) {
		return orderInfoMapper.queryOrdersByMonth(paramMap);
	}

	private void convertUnitAndDesc(OrderInfo order) {
		if (null != order.getPrice()) {
			order.setPrice(UnitConvertUtil.convertUnit(new BigDecimal(order.getPrice()), 1).floatValue());
		}
		if (null != order.getRewardAmount()) {
			order.setRewardAmount(UnitConvertUtil.convertUnit(new BigDecimal(order.getRewardAmount()), 1).floatValue());
		}
		if (null != order.getRent()) {
			order.setRent(UnitConvertUtil.convertUnit(new BigDecimal(order.getRent()), 1).floatValue());
		}
		if (null != order.getDeposit()) {
			order.setDeposit(UnitConvertUtil.convertUnit(new BigDecimal(order.getDeposit()), 1).floatValue());
		}
		if (null != order.getMaxFee()) {
			order.setMaxFee(UnitConvertUtil.convertUnit(new BigDecimal(order.getMaxFee()), 1).floatValue());
		}
		if (null != order.getSpecialMaxFee()) {
			order.setSpecialMaxFee(UnitConvertUtil.convertUnit(new BigDecimal(order.getSpecialMaxFee()), 1)
					.floatValue());
		}
		List<OrderProcessInfo> lstOrderProcess = order.getOrderProcess();
		if (null != lstOrderProcess) {
			for (OrderProcessInfo orderProcess : lstOrderProcess) {
				OrderDesc.convertOrderDesc(orderProcess);
			}
		}
	}

	/**
	 * 转换业务员头像路径URL
	 * @param order
	 */
	private void convertImageUrl(OrderInfo order) {
		if (!StringUtils.isEmpy(order.getSalesHeadImg())) {
			order.setSalesHeadImg(ImageUtil.setImagesOriginalPath(order.getSalesHeadImg()));
		}
	}
	
	/**
	 * 订单删除
	 */
	@Override
	public String deleteOrder(OrderInfo order) throws ServiceException {
		orderInfoMapper.updateOrder(order);
		return "0";
	}
}
