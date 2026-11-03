/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.pubsub.redis.broadcast;

import com.netflix.spinnaker.kork.pubsub.PubsubBroadcastPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubBroadcastSubscribers;
import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastPublisher;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastSubscriber;
import com.netflix.spinnaker.kork.pubsub.model.PubsubPublisher;
import com.netflix.spinnaker.kork.pubsub.model.PubsubSubscriber;
import com.netflix.spinnaker.kork.pubsub.redis.broadcast.api.RedisBroadcastMessageHandlerFactory;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.redis.inbound.RedisInboundChannelAdapter;
import org.springframework.integration.redis.outbound.RedisPublishingMessageHandler;
import org.springframework.stereotype.Component;

/**
 * Wires one {@link RedisInboundChannelAdapter}/{@link RedisPublishingMessageHandler} pair per
 * configured broadcast subscription. Every instance runs this provider and subscribes
 * independently, so Redis' native Pub/Sub fan-out gives broadcast semantics for free - no consumer
 * group, no shared state.
 *
 * <p>These Spring Integration endpoints are constructed dynamically (one per config-driven
 * subscription) rather than declared as {@code @Bean} methods, so they don't go through the normal
 * Spring bean lifecycle automatically. {@link
 * org.springframework.integration.context.IntegrationObjectSupport} (their common superclass) only
 * strictly needs a {@code BeanFactory} and {@code afterPropertiesSet()} for this point-to-point
 * wiring (no channel-name resolution, no AOP), so this provider supplies exactly those two calls
 * itself rather than pulling in the full {@code AutowireCapableBeanFactory} bean-creation
 * machinery.
 */
@Component
@ConditionalOnProperty({"pubsub.enabled", "pubsub.redis.enabled"})
public class RedisBroadcastProvider implements DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(RedisBroadcastProvider.class);

  private final RedisPubsubProperties properties;
  private final PubsubBroadcastPublishers pubsubBroadcastPublishers;
  private final PubsubBroadcastSubscribers pubsubBroadcastSubscribers;
  private final PubsubPublishers pubsubPublishers;
  private final PubsubSubscribers pubsubSubscribers;
  private final RedisBroadcastMessageHandlerFactory messageHandlerFactory;
  private final RedisConnectionFactory redisConnectionFactory;
  private final ApplicationContext applicationContext;
  private final MeterRegistry meterRegistry;

  private final List<RedisNativePubsubSubscriber> subscribers = new ArrayList<>();
  private final List<ExecutorService> executors = new ArrayList<>();

  @Autowired
  public RedisBroadcastProvider(
      RedisPubsubProperties properties,
      PubsubBroadcastPublishers pubsubBroadcastPublishers,
      PubsubBroadcastSubscribers pubsubBroadcastSubscribers,
      PubsubPublishers pubsubPublishers,
      PubsubSubscribers pubsubSubscribers,
      RedisBroadcastMessageHandlerFactory messageHandlerFactory,
      RedisConnectionFactory redisConnectionFactory,
      ApplicationContext applicationContext,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.pubsubBroadcastPublishers = pubsubBroadcastPublishers;
    this.pubsubBroadcastSubscribers = pubsubBroadcastSubscribers;
    this.pubsubPublishers = pubsubPublishers;
    this.pubsubSubscribers = pubsubSubscribers;
    this.messageHandlerFactory = messageHandlerFactory;
    this.redisConnectionFactory = redisConnectionFactory;
    this.applicationContext = applicationContext;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void start() {
    List<PubsubBroadcastPublisher> broadcastPublishers = new ArrayList<>();
    List<PubsubBroadcastSubscriber> broadcastSubscribers = new ArrayList<>();
    List<PubsubPublisher> publishers = new ArrayList<>();
    List<PubsubSubscriber> subscriberRegistrations = new ArrayList<>();

    properties
        .getBroadcasts()
        .forEach(
            subscription -> {
              log.info("Bootstrapping Redis broadcast channel {}", subscription.getChannel());

              RedisPublishingMessageHandler publishingHandler =
                  new RedisPublishingMessageHandler(redisConnectionFactory);
              publishingHandler.setSerializer(StringRedisSerializer.UTF_8);
              publishingHandler.setTopic(subscription.getChannel());
              publishingHandler.setBeanFactory(applicationContext);
              publishingHandler.afterPropertiesSet();

              RedisNativePubsubPublisher publisher =
                  new RedisNativePubsubPublisher(subscription, publishingHandler, meterRegistry);
              broadcastPublishers.add(publisher);
              publishers.add(publisher);

              RedisInboundChannelAdapter adapter =
                  new RedisInboundChannelAdapter(redisConnectionFactory);
              adapter.setSerializer(StringRedisSerializer.UTF_8);
              adapter.setTopics(subscription.getChannel());
              adapter.setTaskExecutor(newExecutor(subscription));

              RedisNativePubsubSubscriber subscriber =
                  new RedisNativePubsubSubscriber(
                      subscription,
                      adapter,
                      messageHandlerFactory.create(subscription),
                      meterRegistry);
              adapter.setBeanFactory(applicationContext);
              adapter.afterPropertiesSet();

              subscribers.add(subscriber);
              broadcastSubscribers.add(subscriber);
              subscriberRegistrations.add(subscriber);
            });

    pubsubBroadcastPublishers.putAll(broadcastPublishers);
    pubsubBroadcastSubscribers.putAll(broadcastSubscribers);
    pubsubPublishers.putAll(publishers);
    pubsubSubscribers.putAll(subscriberRegistrations);

    // Only start listening once everything is registered.
    subscribers.forEach(RedisNativePubsubSubscriber::start);
  }

  /**
   * The adapter's listener container runs both its (blocking) subscription loop and every message
   * handler on this executor; without one it spawns a thread per message. Two threads - one parked
   * on the subscription, one running handlers - keep messages in order, and a bounded queue sheds
   * load when handlers fall behind, which at-most-once delivery permits.
   */
  private ExecutorService newExecutor(
      RedisPubsubProperties.RedisBroadcastSubscription subscription) {
    AtomicInteger threadNumber = new AtomicInteger();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(subscription.getQueueCapacity()),
            runnable -> {
              Thread thread =
                  new Thread(
                      runnable,
                      "pubsub-redis-broadcast-"
                          + subscription.getName()
                          + "-"
                          + threadNumber.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            },
            (runnable, pool) -> {
              meterRegistry
                  .counter(
                      "pubsub.redis.broadcastDropped",
                      List.of(Tag.of("subscription", subscription.getName())))
                  .increment();
              log.warn(
                  "Broadcast handler for {} is falling behind; dropping a message",
                  subscription.getName());
            });
    executors.add(executor);
    return executor;
  }

  @Override
  public void destroy() {
    subscribers.forEach(RedisNativePubsubSubscriber::stop);
    executors.forEach(ExecutorService::shutdownNow);
  }
}
