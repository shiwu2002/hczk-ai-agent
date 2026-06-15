package com.hczk.hczkaiagentserver.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 
 * 配置异步计费消息队列，实现：
 * - Direct Exchange 路由模式
 * - 持久化队列（lazy模式，适合大量消息堆积）
 * - JSON消息序列化
 * 
 * 队列设计：
 * - 交换机: billing.exchange
 * - 队列: billing.queue
 * - 路由键: billing.routing.key
 */
@Configuration
public class RabbitMQConfig {

    /** 计费消息交换机名称 */
    public static final String BILLING_EXCHANGE = "billing.exchange";
    /** 计费消息队列名称 */
    public static final String BILLING_QUEUE = "billing.queue";
    /** 计费消息路由键 */
    public static final String BILLING_ROUTING_KEY = "billing.routing.key";

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPassword;

    /**
     * 创建RabbitMQ连接工厂
     * 
     * @return CachingConnectionFactory 连接工厂
     */
    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitHost, rabbitPort);
        factory.setUsername(rabbitUsername);
        factory.setPassword(rabbitPassword);
        factory.setConnectionTimeout(30000);
        return factory;
    }

    /**
     * 创建RabbitTemplate
     * 
     * @param connectionFactory 连接工厂
     * @return RabbitTemplate 消息模板
     */
    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter());
        template.setMandatory(true);
        return template;
    }

    /**
     * JSON消息转换器
     * 
     * @return Jackson2JsonMessageConverter JSON转换器
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 创建计费消息交换机
     * 
     * @return DirectExchange 直连交换机
     */
    @Bean
    public DirectExchange billingExchange() {
        return new DirectExchange(BILLING_EXCHANGE, true, false);
    }

    /**
     * 创建计费消息队列
     * 使用lazy模式，将消息持久化到磁盘，适合大量消息堆积场景
     * 
     * @return Queue 队列
     */
    @Bean
    public Queue billingQueue() {
        return QueueBuilder.durable(BILLING_QUEUE)
                .withArgument("x-queue-mode", "lazy")
                .build();
    }

    /**
     * 绑定队列到交换机
     * 
     * @param billingQueue    计费队列
     * @param billingExchange 计费交换机
     * @return Binding 绑定关系
     */
    @Bean
    public Binding billingBinding(Queue billingQueue, DirectExchange billingExchange) {
        return BindingBuilder.bind(billingQueue).to(billingExchange).with(BILLING_ROUTING_KEY);
    }
}