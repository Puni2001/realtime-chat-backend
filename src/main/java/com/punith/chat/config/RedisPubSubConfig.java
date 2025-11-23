package com.punith.chat.config;

import com.punith.chat.ws.WsFanoutSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            WsFanoutSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // subscribe to channels
        container.addMessageListener(subscriber, new ChannelTopic("ws.fanout.messages"));
        container.addMessageListener(subscriber, new ChannelTopic("ws.fanout.read-receipts"));
        container.addMessageListener(subscriber, new ChannelTopic("ws.fanout.message-status"));

        return container;
    }
}
