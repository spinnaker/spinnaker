package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.cats.pubsub.PubSubAgentRunner;
import com.netflix.spinnaker.cats.pubsub.PubSubAgentScheduler;
import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.pubsub.config.RedisPubsubConfig;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires the scheduler to Kork's Redis Streams transport. The existing stream key and consumer group
 * remain stable across upgrades. Agent execution and its SQL mutex remain in Clouddriver.
 *
 * <p>Explicitly imports DataRedisAutoConfiguration alongside RedisPubsubConfig because
 * Clouddriver's Main excludes Redis auto-configuration and RedisPubsubConfig relies on an
 * application-supplied StringRedisTemplate. Redis uses spring.data.redis.*; subscription lifecycle
 * and recovery are supplied by Kork, while the runner supplies execution concurrency and discovery
 * pausing.
 */
@Configuration
@ComponentScan(basePackages = "com.netflix.spinnaker.cats.pubsub")
@ConditionalOnProperty("cats.pubsub.enabled")
@Import({RedisPubsubConfig.class, DataRedisAutoConfiguration.class})
@Alpha
public class PubSubSchedulerConfig {
  @Bean
  public MessageChannel agentExecutionChannel(RedisPubsub pubsub) {
    return pubsub.bindTargetedStream(
        PubSubAgentScheduler.STREAM_KEY,
        PubSubAgentRunner.CONSUMER_GROUP,
        PubSubAgentScheduler.STREAM_KEY);
  }
}
