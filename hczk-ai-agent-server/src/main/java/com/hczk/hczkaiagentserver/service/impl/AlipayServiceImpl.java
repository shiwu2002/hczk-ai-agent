package com.hczk.hczkaiagentserver.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.config.AlipayConfig;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.mapper.RechargeRecordMapper;
import com.hczk.hczkaiagentserver.service.AlipayService;
import com.hczk.hczkaiagentserver.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayServiceImpl implements AlipayService {

    private final AlipayConfig alipayConfig;
    private final BillingService billingService;
    private final RechargeRecordMapper rechargeRecordMapper;

    private volatile AlipayClient alipayClient;

    private AlipayClient getAlipayClient() {
        if (alipayClient == null) {
            synchronized (this) {
                if (alipayClient == null) {
                    String privateKey = alipayConfig.getPrivateKey();
                    String alipayPublicKey = alipayConfig.getAlipayPublicKey();

                    if (privateKey == null || privateKey.isBlank()) {
                        log.error("支付宝私钥为空！环境变量诊断: ALIPAY_PRIVATE_KEY={}, ZFBAPPID={}",
                                System.getenv("ALIPAY_PRIVATE_KEY") != null ? "已设置(len=" + System.getenv("ALIPAY_PRIVATE_KEY").length() + ")" : "未设置",
                                System.getenv("ZFBAPPID") != null ? "已设置" : "未设置");
                        throw new RuntimeException("支付宝私钥未配置，请设置环境变量 ALIPAY_PRIVATE_KEY");
                    }
                    if (alipayPublicKey == null || alipayPublicKey.isBlank()) {
                        throw new RuntimeException("支付宝公钥未配置，请设置环境变量 ALIPAY_PUBLIC_KEY");
                    }

                    // 清理 PEM 格式：移除头尾标记和换行空格
                    privateKey = cleanPemKey(privateKey);
                    alipayPublicKey = cleanPemKey(alipayPublicKey);

                    log.info("支付宝配置: appId={}, privateKeySize={}, alipayPublicKeySize={}, alipayPublicKeyPrefix={}",
                            alipayConfig.getAppId(), privateKey.length(), alipayPublicKey.length(),
                            alipayPublicKey.substring(0, Math.min(20, alipayPublicKey.length())));

                    // 验证公钥格式
                    // 支付宝公钥应以 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A 开头（X.509 格式，约360字符）
                    // MIIE 开头的是私钥（PKCS#8格式，约1600字符）
                    // MIIBCgKCAQEA 开头的是应用公钥（PKCS#1格式）
                    if (alipayPublicKey.startsWith("MIIE")) {
                        throw new RuntimeException(
                            "ALIPAY_PUBLIC_KEY 设置的是【私钥】而非【支付宝公钥】！\n" +
                            "以 MIIE 开头的是私钥，支付宝公钥应以 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A 开头。\n" +
                            "请在支付宝开放平台沙箱页面 → 设置 → 接口加签方式(RSA2) → 获取【支付宝公钥】。"
                        );
                    }
                    if (alipayPublicKey.startsWith("MIIBCgKCAQEA")) {
                        throw new RuntimeException(
                            "ALIPAY_PUBLIC_KEY 设置的是【应用公钥】而非【支付宝公钥】！\n" +
                            "以 MIIBCgKCAQEA 开头的是应用公钥，支付宝公钥应以 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A 开头。\n" +
                            "请在支付宝开放平台沙箱页面 → 设置 → 接口加签方式(RSA2) → 上传应用公钥后，获取返回的【支付宝公钥】。"
                        );
                    }

                    alipayClient = new DefaultAlipayClient(
                            alipayConfig.getGatewayUrl(),
                            alipayConfig.getAppId(),
                            privateKey,
                            alipayConfig.getFormat(),
                            alipayConfig.getCharset(),
                            alipayPublicKey,
                            alipayConfig.getSignType()
                    );
                }
            }
        }
        return alipayClient;
    }

    /**
     * 清理 PEM 格式密钥：移除 BEGIN/END 标记、换行和空格
     * 支持传入纯 Base64 字符串或完整 PEM 格式
     */
    private String cleanPemKey(String key) {
        if (key == null) return null;
        return key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    @Override
    public String createOrder(String userId, BigDecimal amount, String orderNo) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        String bizContent = String.format(
                "{\"out_trade_no\":\"%s\",\"total_amount\":\"%s\",\"subject\":\"%s\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}",
                orderNo, amount.toPlainString(), "桓宸智科AI平台充值"
        );
        request.setBizContent(bizContent);

        try {
            return getAlipayClient().pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            log.error("创建支付宝订单失败: orderNo={}, error={}", orderNo, e.getErrMsg(), e);
            throw new RuntimeException("创建支付宝订单失败: " + e.getErrMsg());
        }
    }

    @Override
    public boolean handleNotify(Map<String, String> params) {
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
            if (!signVerified) {
                log.warn("支付宝回调验签失败: params={}", params);
                return false;
            }
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常: error={}", e.getErrMsg(), e);
            return false;
        }

        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("支付宝回调非成功状态: trade_status={}", tradeStatus);
            return false;
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");

        // 幂等校验：检查该 orderNo 是否已处理过（通过 transactionId 字段查询）
        Long existCount = rechargeRecordMapper.selectCount(
                new LambdaQueryWrapper<RechargeRecord>()
                        .eq(RechargeRecord::getTransactionId, outTradeNo)
        );
        if (existCount > 0) {
            log.info("支付宝回调订单已处理，跳过: outTradeNo={}", outTradeNo);
            return true;
        }

        // 解析 outTradeNo 格式: RCH{timestamp}U{userId}
        String userId;
        try {
            int uIndex = outTradeNo.indexOf("U");
            if (uIndex < 0) {
                log.error("支付宝回调订单号格式错误: outTradeNo={}", outTradeNo);
                return false;
            }
            userId = outTradeNo.substring(uIndex + 1);
        } catch (Exception e) {
            log.error("支付宝回调订单号解析userId失败: outTradeNo={}", outTradeNo, e);
            return false;
        }

        try {
            RechargeRecord record = billingService.recharge(userId, new BigDecimal(totalAmount), "alipay");
            // 将 transactionId 更新为支付宝订单号，用于幂等校验
            record.setTransactionId(outTradeNo);
            rechargeRecordMapper.updateById(record);
            log.info("支付宝回调充值成功: userId={}, amount={}, outTradeNo={}, tradeNo={}",
                    userId, totalAmount, outTradeNo, tradeNo);
            return true;
        } catch (Exception e) {
            log.error("支付宝回调充值失败: userId={}, amount={}, outTradeNo={}",
                    userId, totalAmount, outTradeNo, e);
            return false;
        }
    }

    @Override
    public boolean queryOrderStatus(String orderNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(String.format("{\"out_trade_no\":\"%s\"}", orderNo));

        try {
            var response = getAlipayClient().execute(request);
            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
            }
            return false;
        } catch (AlipayApiException e) {
            log.error("查询支付宝订单状态失败: orderNo={}, error={}", orderNo, e.getErrMsg(), e);
            return false;
        }
    }
}
