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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * KubernetesQueueBatcher reads items from an input queue and batches them into output queue. The
 * actual batch size is determined by the maxSize parameter and the timeoutMillis parameter. The
 * batcher will wait for items to arrive in the input queue until either the batch size is reached
 * or the timeout occurs. If the timeout occurs, the batcher will send whatever items it has
 * collected so far to the output queue.
 */
@Slf4j
class KubernetesQueueBatcher<T> implements Runnable {

  private final BlockingQueue<T> inQueue;
  private final BlockingQueue<List<T>> outQueue;
  private final int maxSize;
  private final long timeoutMillis;

  public KubernetesQueueBatcher(
      BlockingQueue<T> inQueue, BlockingQueue<List<T>> outQueue, int maxSize, long timeoutMillis) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize must be greater than 0");
    }
    if (timeoutMillis <= 0) {
      throw new IllegalArgumentException("timeoutMillis must be greater than 0");
    }

    this.inQueue = inQueue;
    this.outQueue = outQueue;
    this.maxSize = maxSize;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public void run() {
    List<T> batch = null;
    while (!Thread.currentThread().isInterrupted()) {
      try {
        batch = new ArrayList<>();

        // Poll the first item indefinitely to ensure we start the batch
        T firstItem = inQueue.take();
        batch.add(firstItem);

        long batchStartTime = System.currentTimeMillis();
        long spentTime;
        while (batch.size() < maxSize
            && (spentTime = System.currentTimeMillis() - batchStartTime) < timeoutMillis) {
          long remainingTime = timeoutMillis - spentTime;
          T item = inQueue.poll(remainingTime, TimeUnit.MILLISECONDS);
          if (item != null) { // has not timed out
            batch.add(item);
          }
        }

        // Send the batch to the outQueue. At this point, we know the batch is not empty and
        // contains at least one item.
        outQueue.put(batch);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // try to send the batch to the outQueue if it is not empty, and the thread is interrupted
    if (batch != null && !batch.isEmpty()) {
      // this is a non-blocking call to avoid deadlocks if the outQueue is full, and the consumer
      // thread is interrupted already
      boolean ignore = outQueue.offer(batch);
    }
  }
}
