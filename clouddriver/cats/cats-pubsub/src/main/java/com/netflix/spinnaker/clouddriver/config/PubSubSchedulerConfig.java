package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.cats.pubsub.PubSubAgentRunner;
import com.netflix.spinnaker.cats.pubsub.PubSubAgentScheduler;
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties;

import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ComponentScan(basePackages = "com.netflix.spinnaker.cats.pubsub")
@ConditionalOnProperty("cats.pubsub.enabled")
@Log4j2
public class PubSubSchedulerConfig {

  @Bean
  PubSubAgentRunner listener() {
    return new PubSubAgentRunner();
  }

  @Bean
  MessageListenerAdapter messageListenerAdapter(PubSubAgentRunner listener) {
    return new MessageListenerAdapter(listener, "onMessage");
  }

  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(
    RedisConnectionFactory connectionFactory, MessageListenerAdapter listener) {

    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setMaxPoolSize(300);
    taskExecutor.setCorePoolSize(100);
    taskExecutor.initialize();

    log.info("Starting RedisMessageListenerContainer & pubsub processing of scheduled agents");
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setTaskExecutor(taskExecutor);
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, ChannelTopic.of(PubSubAgentScheduler.CHANNEL));
    return container;
  }

}
