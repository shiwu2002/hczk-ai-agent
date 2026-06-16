package com.hczk.hczkaiagentserver.service;

import java.math.BigDecimal;
import java.util.Map;

public interface AlipayService {
    String createOrder(Long userId, BigDecimal amount, String orderNo);
    boolean handleNotify(Map<String, String> params);
    boolean queryOrderStatus(String orderNo);
}
