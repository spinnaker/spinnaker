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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(1) // Default timeout for all tests in this class to avoid stuck threads
class KubernetesQueueProcessorTest {

  private BlockingQueue<String> queue;
  private KubernetesQueueProcessor<String> queueProcessor;
  private BlockingQueue<String> handledItems;

  @BeforeEach
  void init() {
    handledItems = new ArrayBlockingQueue<>(50);
    queue = new ArrayBlockingQueue<>(50);
    queueProcessor = new KubernetesQueueProcessor<>(queue, handledItems::add);
  }

  @Test
  void emptyQueue() {
    Thread processor = startQueueProcessor();
    processor.interrupt();
    try {
      processor.join();
    } catch (InterruptedException e) {
      Assertions.fail("the test thread was interrupted");
    }

    assertThat(handledItems).isEmpty();
  }

  @Test
  void testOneItem() throws InterruptedException {
    Thread processor = startQueueProcessor();
    queue.add("item1");

    try {
      String processedItem = handledItems.take();
      assertThat(processedItem).isEqualTo("item1");
    } finally {
      processor.interrupt();
    }
  }

  @Test
  void testMultipleItems() throws InterruptedException {
    Thread processor = startQueueProcessor();
    queue.add("item1");
    queue.add("item2");

    try {
      String processedItem1 = handledItems.take();
      String processedItem2 = handledItems.take();
      assertThat(processedItem1).isEqualTo("item1");
      assertThat(processedItem2).isEqualTo("item2");
    } finally {
      processor.interrupt();
    }
  }

  @Test
  void testNullExceptionHandler() throws InterruptedException {
    queueProcessor =
        new KubernetesQueueProcessor<>(
            queue,
            item -> {
              if (item.equals("item1")) {
                throw new RuntimeException("Test exception");
              }
              handledItems.add(item);
            },
            null); // No exception handler provided
    Thread processor = startQueueProcessor();
    queue.add("item1");
    queue.add("item2");

    try {
      String processedItem = handledItems.take();
      assertThat(processedItem).isEqualTo("item2"); // item1 should be skipped
    } finally {
      processor.interrupt();
    }
  }

  @Test
  void customExceptionHandler() throws InterruptedException {
    queueProcessor =
        new KubernetesQueueProcessor<>(
            queue,
            item -> {
              if (item.equals("item1")) {
                throw new RuntimeException("Test exception");
              }
              handledItems.add(item);
            },
            (item, e) ->
                handledItems.add("Handled exception for: " + item)); // Custom exception handler

    Thread processor = startQueueProcessor();
    queue.add("item1");
    queue.add("item2");

    try {
      String processedItem = handledItems.take();
      assertThat(processedItem).isEqualTo("Handled exception for: item1"); // Exception handled
      String processedItem2 = handledItems.take();
      assertThat(processedItem2).isEqualTo("item2"); // item2 should be processed normally
    } finally {
      processor.interrupt();
    }
  }

  private Thread startQueueProcessor() {
    Thread thread = new Thread(queueProcessor);
    thread.start();
    return thread;
  }
}
