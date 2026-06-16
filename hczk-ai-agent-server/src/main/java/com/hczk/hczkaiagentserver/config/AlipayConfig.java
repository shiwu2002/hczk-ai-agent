package com.hczk.hczkaiagentserver.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {
    private Boolean enabled = false;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String notifyUrl;
    private String returnUrl;
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String format = "json";

    @PostConstruct
    public void init() {
        // 始终尝试从环境变量回退，不受 enabled 控制
        // 因为 enabled 本身可能也需要从环境变量读取
        if (appId == null || appId.isBlank()) {
            appId = getEnvAny("ZFBAPPID", "ALIPAY_APP_ID");
        }
        if (privateKey == null || privateKey.isBlank()) {
            privateKey = getEnvAny("ALIPAY_PRIVATE_KEY");
        }
        if (alipayPublicKey == null || alipayPublicKey.isBlank()) {
            alipayPublicKey = getEnvAny("ALIPAY_PUBLIC_KEY");
        }
        if (enabled == null || !enabled) {
            String envEnabled = getEnvAny("ALIPAY_ENABLED");
            if ("true".equalsIgnoreCase(envEnabled)) {
                enabled = true;
            }
        }

        log.info("支付宝配置: enabled={}, appId={}, privateKeySize={}, alipayPublicKeySize={}, notifyUrl={}",
                enabled,
                appId == null ? "NULL" : appId,
                privateKey == null ? "NULL" : privateKey.length(),
                alipayPublicKey == null ? "NULL" : alipayPublicKey.length(),
                notifyUrl);

        if (Boolean.TRUE.equals(enabled)) {
            if (privateKey == null || privateKey.isBlank()) {
                log.error("支付宝私钥为空！请设置环境变量 ALIPAY_PRIVATE_KEY");
            }
            if (alipayPublicKey == null || alipayPublicKey.isBlank()) {
                log.error("支付宝公钥为空！请设置环境变量 ALIPAY_PUBLIC_KEY");
            }
        }
    }

    private String getEnvAny(String... names) {
        for (String name : names) {
            String val = System.getenv(name);
            if (val != null && !val.isBlank()) {
                log.info("支付宝: 从环境变量 {} 读取到值 (len={})", name, val.length());
                return val;
            }
        }
        return null;
    }
}
