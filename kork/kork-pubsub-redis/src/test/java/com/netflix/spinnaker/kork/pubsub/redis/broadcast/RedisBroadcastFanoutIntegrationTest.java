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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.pubsub.model.BroadcastMessage;
import com.netflix.spinnaker.kork.pubsub.model.PubsubBroadcastMessageHandler;
import com.netflix.spinnaker.kork.pubsub.redis.config.RedisPubsubProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.redis.inbound.RedisInboundChannelAdapter;
import org.springframework.integration.redis.outbound.RedisPublishingMessageHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the broadcast contract concretely against a real Valkey instance: every
 * currently-listening subscriber receives a published message (fan-out), and a subscriber that
 * starts listening after a publish never sees it - the {@code AT_MOST_ONCE} guarantee documented on
 * {@link com.netflix.spinnaker.kork.pubsub.model.DeliveryGuarantee}.
 */
class RedisBroadcastFanoutIntegrationTest {

  private static GenericContainer<?> valkey;
  private static LettuceConnectionFactory connectionFactory;

  @BeforeAll
  static void startValkey() {
    valkey =
        new GenericContainer<>(DockerImageName.parse("valkey/valkey:8")).withExposedPorts(6379);
    valkey.start();
    connectionFactory = new LettuceConnectionFactory(valkey.getHost(), valkey.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
  }

  @AfterAll
  static void stopValkey() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
    if (valkey != null) {
      valkey.stop();
    }
  }

  @Test
  void everyListenerReceivesAPublishedMessage_butALateListenerNeverSeesAnEarlierOne()
      throws InterruptedException {
    String channel = "test-channel-" + UUID.randomUUID();
    RedisPubsubProperties.RedisBroadcastSubscription subscription =
        new RedisPubsubProperties.RedisBroadcastSubscription();
    subscription.setName("test-broadcast");
    subscription.setChannel(channel);

    RecordingHandler recorderA = new RecordingHandler();
    RecordingHandler recorderB = new RecordingHandler();
    RecordingHandler recorderC = new RecordingHandler();

    RedisNativePubsubSubscriber subscriberA = listener(subscription, recorderA);
    RedisNativePubsubSubscriber subscriberB = listener(subscription, recorderB);
    RedisNativePubsubSubscriber subscriberC = listener(subscription, recorderC);
    subscriberA.start();
    subscriberB.start();
    subscriberC.start();
    // give the SUBSCRIBE commands time to register with the broker before publishing
    Thread.sleep(300);

    RedisPublishingMessageHandler publishingHandler =
        new RedisPublishingMessageHandler(connectionFactory);
    publishingHandler.setSerializer(StringRedisSerializer.UTF_8);
    publishingHandler.setTopic(channel);
    publishingHandler.setBeanFactory(
        new org.springframework.context.support.GenericApplicationContext());
    publishingHandler.afterPropertiesSet();
    RedisNativePubsubPublisher publisher =
        new RedisNativePubsubPublisher(subscription, publishingHandler, new SimpleMeterRegistry());

    publisher.publish("hello");

    awaitAllReceived(Duration.ofSeconds(5), recorderA, recorderB, recorderC);

    subscriberA.stop();
    subscriberB.stop();

    assertThat(recorderA.received).extracting(BroadcastMessage::getBody).contains("hello");
    assertThat(recorderB.received).extracting(BroadcastMessage::getBody).contains("hello");
    assertThat(recorderC.received).extracting(BroadcastMessage::getBody).contains("hello");

    // a late subscriber, started after the publish, must never see it
    RecordingHandler lateRecorder = new RecordingHandler();
    RedisNativePubsubSubscriber lateSubscriber = listener(subscription, lateRecorder);
    lateSubscriber.start();
    Thread.sleep(500);
    lateSubscriber.stop();

    assertThat(lateRecorder.received).isEmpty();

    subscriberC.stop();
  }

  private void awaitAllReceived(Duration timeout, RecordingHandler... recorders)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      boolean allReceived = true;
      for (RecordingHandler recorder : recorders) {
        if (recorder.received.isEmpty()) {
          allReceived = false;
          break;
        }
      }
      if (allReceived) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Timed out waiting for all recorders to receive a message");
  }

  private RedisNativePubsubSubscriber listener(
      RedisPubsubProperties.RedisBroadcastSubscription subscription, RecordingHandler handler) {
    RedisInboundChannelAdapter adapter = new RedisInboundChannelAdapter(connectionFactory);
    adapter.setSerializer(StringRedisSerializer.UTF_8);
    adapter.setTopics(subscription.getChannel());
    RedisNativePubsubSubscriber subscriber =
        new RedisNativePubsubSubscriber(subscription, adapter, handler, new SimpleMeterRegistry());
    adapter.setBeanFactory(new org.springframework.context.support.GenericApplicationContext());
    adapter.afterPropertiesSet();
    return subscriber;
  }

  private static class RecordingHandler implements PubsubBroadcastMessageHandler {
    final List<BroadcastMessage> received = new CopyOnWriteArrayList<>();

    @Override
    public void handleMessage(BroadcastMessage message) {
      received.add(message);
    }
  }
}
