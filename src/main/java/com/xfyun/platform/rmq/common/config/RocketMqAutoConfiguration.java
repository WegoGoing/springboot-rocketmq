package com.xfyun.platform.rmq.common.config;

import com.xfyun.platform.rmq.common.common.AbstractRocketMqConsumer;
import com.xfyun.platform.rmq.common.common.DefaultRocketMqProducer;
import com.xfyun.platform.rmq.common.common.RocketMqConsumerMBean;
import com.xfyun.platform.rmq.common.common.RunTimeUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;

/**
 * @Author: gaomq
 * @Date: 2019-04-28 9:16
 */
@Configuration
@ConditionalOnClass({ DefaultMQPushConsumer.class })
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqAutoConfiguration {

    private final static Logger LOGGER = LoggerFactory.getLogger(RocketMqAutoConfiguration.class);

    @Resource
    private RocketMqProperties rocketMqProperties;


    @Bean
    @ConditionalOnClass(DefaultMQProducer.class)
    @ConditionalOnMissingBean(DefaultMQProducer.class)
    public DefaultMQProducer mqProducer() {
        DefaultMQProducer producer = new DefaultMQProducer();
        producer.setProducerGroup(rocketMqProperties.getProducerGroupName());
        producer.setNamesrvAddr(rocketMqProperties.getNameServer());

        producer.setSendMsgTimeout(rocketMqProperties.getProducerSendMsgTimeout());
        producer.setRetryTimesWhenSendFailed(rocketMqProperties.getProducerRetryTimesWhenSendFailed());
        producer.setRetryTimesWhenSendAsyncFailed(rocketMqProperties.getProducerRetryTimesWhenSendAsyncFailed());
        producer.setMaxMessageSize(rocketMqProperties.getProducerMaxMessageSize());
        producer.setCompressMsgBodyOverHowmuch(rocketMqProperties.getProducerCompressMsgBodyOverHowMuch());
        producer.setRetryAnotherBrokerWhenNotStoreOK(rocketMqProperties.isProducerRetryAnotherBrokerWhenNotStoreOk());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("producer shutdown");
            producer.shutdown();
            LOGGER.info("producer has shutdown");
        }));

        try {
            producer.start();
            LOGGER.info("rocketmq producer started, nameserver:{}, group:{}", rocketMqProperties.getNameServer(),
                    rocketMqProperties.getProducerGroupName());
        } catch (MQClientException e) {
            LOGGER.error("producer start error, nameserver:{}, group:{}", rocketMqProperties.getNameServer(),
                    rocketMqProperties.getProducerGroupName(), e);
        }

        return producer;
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean(DefaultMQProducer.class)
    @ConditionalOnMissingBean(name = "defaultRocketMqProducer")
    public DefaultRocketMqProducer defaultRocketMqProducer(@Qualifier("mqProducer") DefaultMQProducer mqProducer) {
        DefaultRocketMqProducer defaultRocketMqProducer = new DefaultRocketMqProducer();
        defaultRocketMqProducer.setProducer(mqProducer);

        return defaultRocketMqProducer;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(value = AbstractRocketMqConsumer.class)
    @Order
    public RocketMqConsumerMBean rocketMqConsumerMBean(List<AbstractRocketMqConsumer> messageListeners) {
        RocketMqConsumerMBean rocketMqConsumerMBean = new RocketMqConsumerMBean();
        messageListeners.forEach(this::registerMQConsumer);
        rocketMqConsumerMBean.setConsumers(messageListeners);

        return rocketMqConsumerMBean;
    }

    @SuppressWarnings("unchecked")
    private void registerMQConsumer(AbstractRocketMqConsumer rocketMqConsumer) {

        DefaultMQPushConsumer mqPushConsumer = rocketMqConsumer.getConsumer();
        mqPushConsumer.setNamesrvAddr(rocketMqProperties.getNameServer());

        mqPushConsumer.setInstanceName(RunTimeUtil.getRocketMqUniqeInstanceName());

        Map<String, String> subscribeTopicTags =rocketMqConsumer.subscribeTopicTags();

        subscribeTopicTags.entrySet().forEach(e -> {
            try {
                String rocketMqTopic = e.getKey();
                String rocketMqTags = e.getValue();
                //默认tag
                if(StringUtils.isEmpty(rocketMqTags)){
                    rocketMqTags = "*";
                }
                mqPushConsumer.subscribe(rocketMqTopic, rocketMqTags);
                LOGGER.info("subscribe, topic:{}, tags:{}", rocketMqTopic, rocketMqTags);
            } catch (MQClientException ex) {
                LOGGER.error("consumer subscribe error", ex);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("consumer shutdown");
            mqPushConsumer.shutdown();
            LOGGER.info("consumer has shutdown");
        }));

        try {
            mqPushConsumer.start();
            rocketMqConsumer.setStarted(true);
            LOGGER.info("rocketmq consumer started, nameserver:{}, group:{}", rocketMqProperties.getNameServer(),
                    rocketMqConsumer.getConsumerGroup());
        } catch (MQClientException e) {
            LOGGER.error("consumer start error, nameserver:{}, group:{}", rocketMqProperties.getNameServer(),
                    rocketMqConsumer.getConsumerGroup(), e);
        }

    }

}
