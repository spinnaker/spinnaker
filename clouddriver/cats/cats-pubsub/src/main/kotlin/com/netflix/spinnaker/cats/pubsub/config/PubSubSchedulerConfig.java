package com.netflix.spinnaker.cats.pubsub.config;

import com.netflix.spinnaker.cats.pubsub.PubSubAgentRunner;
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientConnectionProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URL;
import java.time.Duration;

@Configuration
@ConditionalOnProperty("cats.pubsub.enabled")
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
  RedisTemplate<String, String> redisOperations(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    return template;
  }

  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory, MessageListenerAdapter listener) {

    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, ChannelTopic.of("chatroom"));
    return container;
  }

  @Bean
  @ConditionalOnMissingBean(RedisConnectionFactory.class)
  public RedisConnectionFactory redisConnectionFactory(final RedisConfigurationProperties properties, GenericObjectPoolConfig redisPoolConfig) throws Exception{
    GenericObjectPoolConfig poolConfig = redisPoolConfig.clone();
    //TODO: Move to configuration properties
    poolConfig.setMaxTotal(300);
    poolConfig.setJmxEnabled(true);
    poolConfig.setBlockWhenExhausted(true);
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
    URL url = new URL(properties.getConnection());
    configuration.setHostName(url.getHost());
    configuration.setPort(url.getPort());
    if (StringUtils.isNotEmpty(url.getUserInfo())) {
      configuration.setUsername(url.getUserInfo().split(":")[0]);
      configuration.setPassword(url.getUserInfo().split(":")[1]);
    }
    JedisClientConfiguration.DefaultJedisClientConfigurationBuilder clientConfig = (JedisClientConfiguration.DefaultJedisClientConfigurationBuilder) JedisClientConfiguration.builder();
    clientConfig.readTimeout(Duration.ofMillis(properties.getTimeout()));
    if (url.getProtocol().equals("rediss")) {
      clientConfig.useSsl();
    }

    clientConfig.poolConfig(poolConfig);
    return new JedisConnectionFactory(configuration, clientConfig.build());
  }
}
