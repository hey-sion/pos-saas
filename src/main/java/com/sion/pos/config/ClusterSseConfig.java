package com.sion.pos.config;

import com.sion.pos.support.sse.RedisWaitingOrdersNotifier;
import com.sion.pos.support.sse.RedisWaitingOrdersSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** cluster 프로파일 전용 — 주문 대기 목록 변경 채널 구독 */
@Configuration(proxyBeanMethods = false)
@Profile("cluster")
public class ClusterSseConfig {

    @Bean
    public RedisMessageListenerContainer waitingOrdersListenerContainer(
                                RedisConnectionFactory connectionFactory,
                                RedisWaitingOrdersSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisWaitingOrdersNotifier.CHANNEL));

        return container;
    }
}