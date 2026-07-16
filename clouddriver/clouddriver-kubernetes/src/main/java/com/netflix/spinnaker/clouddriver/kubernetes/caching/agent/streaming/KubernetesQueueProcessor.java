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

import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * KubernetesQueueProcessor is a simple queue processor that takes items from a BlockingQueue,
 * processes them using a Consumer, and handles exceptions that occur during processing. It's
 * supposed to be run in a separate thread.
 */
@Slf4j
class KubernetesQueueProcessor<T> implements Runnable {

  private final BlockingQueue<T> queue;
  private final Consumer<T> consumer;
  private final KubernetesQueueProcessorExceptionHandler<T> exceptionHandler;

  public KubernetesQueueProcessor(BlockingQueue<T> queue, Consumer<T> consumer) {
    this(queue, consumer, null);
  }

  public KubernetesQueueProcessor(
      BlockingQueue<T> queue,
      Consumer<T> consumer,
      KubernetesQueueProcessorExceptionHandler<T> exceptionHandler) {
    this.queue = queue;
    this.consumer = consumer;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      T item = null;
      try {
        item = queue.take();
        consumer.accept(item);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        log.error("Error processing queue", e);
        if (exceptionHandler != null) {
          exceptionHandler.handle(item, e);
        }
      }
    }
  }
}
