/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.netflix.spinnaker.kork.pubsub.local.LocalMessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalMessageChannelTest {
  @Test
  void competesWithinOneProcessWithoutRedis() {
    LocalMessageChannel channel = new LocalMessageChannel("work");
    List<String> first = new CopyOnWriteArrayList<>();
    List<String> second = new CopyOnWriteArrayList<>();
    channel.publish("before-start", Map.of("kind", "work"));
    try (MessageSubscription a = subscriber(channel, first);
        MessageSubscription b = subscriber(channel, second)) {
      a.start();
      b.start();
      for (int i = 0; i < 100; i++) {
        channel.publish("job-" + i);
      }
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(first.size() + second.size()).isEqualTo(101));
      assertThat(first).doesNotContainAnyElementsOf(second);
      assertThat(channel.statistics().depth()).isZero();
    }
  }

  @Test
  void closingConsumerReturnsUnacknowledgedWork() {
    LocalMessageChannel channel = new LocalMessageChannel("work");
    AtomicReference<PubsubMessage> received = new AtomicReference<>();
    MessageSubscription failed = channel.subscribe("failed", 1, received::set, error -> {});
    failed.start();
    channel.publish("retry");
    await().until(() -> received.get() != null);
    failed.close();
    List<String> recovered = new CopyOnWriteArrayList<>();
    try (MessageSubscription survivor = subscriber(channel, recovered)) {
      survivor.start();
      await().untilAsserted(() -> assertThat(recovered).containsExactly("retry"));
    }
  }

  private MessageSubscription subscriber(LocalMessageChannel channel, List<String> messages) {
    AtomicReference<MessageSubscription> reference = new AtomicReference<>();
    MessageSubscription subscription =
        channel.subscribe(
            "worker",
            1,
            message -> {
              messages.add(message.payload());
              reference.get().ack(message);
            },
            error -> {
              throw new AssertionError(error);
            });
    reference.set(subscription);
    return subscription;
  }
}
