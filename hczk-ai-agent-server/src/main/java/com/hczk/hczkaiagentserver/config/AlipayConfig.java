package com.hczk.hczkaiagentserver.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝支付配置类
 * 绑定 application.yaml 中 alipay 前缀的配置项
 * 支持沙箱环境和正式环境切换，环境变量优先于配置文件
 *
 * 环境变量说明：
 * - ZFBAPPID          : 沙箱/正式应用ID
 * - ALIPAY_PRIVATE_KEY: 应用私钥（MIIE开头，用于签名请求）
 * - ALIPAY_PUBLIC_KEY : 支付宝公钥（MIIBIjANBgk开头，用于验签回调，非应用公钥）
 * - ALIPAY_ENABLED    : 是否启用支付宝支付（true/false）
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    /** 是否启用支付宝支付 */
    private Boolean enabled = false;

    /** 应用ID（沙箱或正式） */
    private String appId;

    /** 应用私钥（PKCS8格式，纯Base64或PEM格式均可） */
    private String privateKey;

    /** 支付宝公钥（从开放平台获取，非应用公钥） */
    private String alipayPublicKey;

    /** 支付宝网关地址（沙箱/正式） */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 异步通知地址（支付成功后支付宝服务器回调） */
    private String notifyUrl;

    /** 同步跳转地址（支付完成后浏览器跳转） */
    private String returnUrl;

    /** 签名算法，默认RSA2（SHA256WithRSA） */
    private String signType = "RSA2";

    /** 编码格式 */
    private String charset = "UTF-8";

    /** 返回数据格式 */
    private String format = "json";

    /**
     * 初始化：从环境变量回退读取配置
     * Spring Boot的@ConfigurationProperties绑定环境变量时，
     * 会将kebab-case转为UPPER_SNAKE_CASE（如alipay-private-key → ALIPAY_PRIVATE_KEY），
     * 此处通过System.getenv()兼容多种命名格式
     */
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

    /** 按优先级依次尝试从多个环境变量名读取值 */
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
