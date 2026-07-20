/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(1) // Default timeout for all tests in this class to avoid stuck threads
class KubernetesQueueBatcherTest {

  private static final int BATCH_SIZE = 2;

  private BlockingQueue<String> inQueue;
  private BlockingQueue<List<String>> outQueue;
  private KubernetesQueueBatcher<String> queueBatcher;

  @BeforeEach
  void init() {
    // some tests rely on this. rewrite the tests if you change this
    assertThat(BATCH_SIZE).isGreaterThanOrEqualTo(2);

    inQueue = new ArrayBlockingQueue<>(50);
    outQueue = new ArrayBlockingQueue<>(50);
    queueBatcher = new KubernetesQueueBatcher<>(inQueue, outQueue, BATCH_SIZE, 200);
  }

  @Test
  void invalidBatchSize() {
    assertThatThrownBy(() -> new KubernetesQueueBatcher<>(inQueue, outQueue, 0, 200))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSize must be greater than 0");
  }

  @Test
  void invalidTimeout() {
    assertThatThrownBy(() -> new KubernetesQueueBatcher<>(inQueue, outQueue, 2, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeoutMillis must be greater than 0");
  }

  @Test
  void emptyQueue() {
    Thread batcher = startBatcherThread();
    batcher.interrupt();
    try {
      batcher.join();
    } catch (InterruptedException e) {
      Assertions.fail("the test thread was interrupted");
    }
  }

  @Test
  void testOneItem() throws InterruptedException {
    Thread batcher = startBatcherThread();
    inQueue.add("item1");

    try {
      List<String> batch = outQueue.take();
      assertThat(batch).containsExactlyInAnyOrder("item1");
    } finally {
      batcher.interrupt();
    }
  }

  @Test
  void fullBatch() throws InterruptedException {
    Thread batcher = startBatcherThread();
    inQueue.add("item1");
    inQueue.add("item2");

    try {
      List<String> batch = outQueue.take();
      assertThat(batch).containsExactlyInAnyOrder("item1", "item2");
    } finally {
      batcher.interrupt();
    }
  }

  @Test
  void multipleBatches() throws InterruptedException {
    Thread batcher = startBatcherThread();
    inQueue.add("item1");
    inQueue.add("item2");
    inQueue.add("item3");
    inQueue.add("item4");
    inQueue.add("item5");

    try {
      List<String> batch1 = outQueue.take();
      assertThat(batch1).containsExactlyInAnyOrder("item1", "item2");

      List<String> batch2 = outQueue.take();
      assertThat(batch2).containsExactlyInAnyOrder("item3", "item4");

      List<String> batch3 = outQueue.take();
      assertThat(batch3).containsExactlyInAnyOrder("item5");
    } finally {
      batcher.interrupt();
    }
  }

  private Thread startBatcherThread() {
    Thread thread = new Thread(queueBatcher);
    thread.start();
    return thread;
  }
}
