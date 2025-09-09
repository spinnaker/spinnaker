package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.cats.pubsub.PubSubAgentRunner;
import com.netflix.spinnaker.cats.pubsub.PubSubAgentScheduler;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
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
      PubSubSchedulerProperties properties,
      RedisConnectionFactory connectionFactory,
      MessageListenerAdapter listener) {
    log.info("Starting message listener with " + properties.getMaxConcurrentAgents() + " agents");
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setThreadNamePrefix("pubsub-agent-runner-");
    taskExecutor.setMaxPoolSize(properties.getMaxConcurrentAgents());
    taskExecutor.setCorePoolSize(properties.getMaxConcurrentAgents() / 3);
    taskExecutor.setQueueCapacity(0);
    taskExecutor.initialize();

    log.info("Starting RedisMessageListenerContainer & pubsub processing of scheduled agents");
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setTaskExecutor(taskExecutor);
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, ChannelTopic.of(PubSubAgentScheduler.CHANNEL));
    return container;
  }
}
