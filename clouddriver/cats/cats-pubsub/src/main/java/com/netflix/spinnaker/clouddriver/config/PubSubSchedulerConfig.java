package com.netflix.spinnaker.clouddriver.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.pubsub.PubSubAgentRunner;
import com.netflix.spinnaker.cats.pubsub.PubSubAgentScheduler;
import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager;
import com.netflix.spinnaker.kork.lock.LockManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ComponentScan(basePackages = "com.netflix.spinnaker.cats.pubsub")
@ConditionalOnProperty("cats.pubsub.enabled")
@ImportAutoConfiguration(
    classes = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@Log4j2
@Alpha
public class PubSubSchedulerConfig {

  @Bean
  RedisMessageListenerContainer redisMessageListenerContainer(
      PubSubSchedulerProperties properties,
      RedisConnectionFactory connectionFactory,
      MeterRegistry meterRegistry,
      PubSubAgentRunner listener) {
    log.info("Starting message listener with " + properties.getMaxConcurrentAgents() + " agents");
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setThreadNamePrefix("pubsub-agent-runner-");
    // core == max with idle timeout: a ThreadPoolExecutor only grows past core once its queue is
    // FULL, so a smaller core with a bounded queue would cap concurrency at the core size until
    // the backlog saturates - the opposite of what we want.
    taskExecutor.setMaxPoolSize(properties.getMaxConcurrentAgents());
    taskExecutor.setCorePoolSize(properties.getMaxConcurrentAgents());
    taskExecutor.setAllowCoreThreadTimeOut(true);
    // Bounded queue as burst buffer: pub/sub delivers ALL due agents at once, and a rejected
    // dispatch means the message is simply lost until the stale-PENDING sweep requeues it many
    // minutes later.  Rejections are still possible when the queue overflows - count them so
    // saturation is visible.
    taskExecutor.setQueueCapacity(properties.getRunnerQueueCapacity());
    taskExecutor.setRejectedExecutionHandler(
        (runnable, executor) -> {
          meterRegistry.counter("cats.pubsub.runner.rejected").increment();
          log.warn(
              "Agent runner pool saturated ({} threads, queue capacity {}) - dropping a message.  The affected agent stays PENDING until the stale-pending sweep requeues it.  Consider raising cats.pubsub.maxConcurrentAgents or runnerQueueCapacity.",
              properties.getMaxConcurrentAgents(),
              properties.getRunnerQueueCapacity());
        });
    taskExecutor.initialize();

    log.info("Starting RedisMessageListenerContainer & pubsub processing of scheduled agents");
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setTaskExecutor(taskExecutor);
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(listener, ChannelTopic.of(PubSubAgentScheduler.CHANNEL));
    return container;
  }

  @Bean
  LockManager redisLockManager(
      Clock clock, Registry registry, RedisClientDelegate redisClientDelegate) {
    ObjectMapper objectMapper =
        new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    return new RedisLockManager(
        null, // will fall back to running node name
        clock,
        registry,
        objectMapper,
        redisClientDelegate,
        Optional.empty(),
        Optional.empty());
  }
}
