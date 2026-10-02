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
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
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

              RedisNativePubsubSubscriber subscriber =
                  new RedisNativePubsubSubscriber(
                      subscription,
                      adapter,
                      messageHandlerFactory.create(subscription),
                      meterRegistry);
              adapter.setBeanFactory(applicationContext);
              adapter.afterPropertiesSet();
              subscriber.start();

              subscribers.add(subscriber);
              broadcastSubscribers.add(subscriber);
              subscriberRegistrations.add(subscriber);
            });

    pubsubBroadcastPublishers.putAll(broadcastPublishers);
    pubsubBroadcastSubscribers.putAll(broadcastSubscribers);
    pubsubPublishers.putAll(publishers);
    pubsubSubscribers.putAll(subscriberRegistrations);
  }

  @Override
  public void destroy() {
    subscribers.forEach(RedisNativePubsubSubscriber::stop);
  }
}
