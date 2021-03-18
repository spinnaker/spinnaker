/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.requestqueue.pooled;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RequestDistributor implements Runnable {
  private final AtomicBoolean continueRunning = new AtomicBoolean(true);
  private final PollCoordinator pollCoordinator;
  private final Executor executor;
  private final Collection<Queue<PooledRequest<?>>> requestQueues;
  private final Counter submissionCounter;

  private final Logger log = LoggerFactory.getLogger(getClass());

  RequestDistributor(
      Registry registry,
      PollCoordinator pollCoordinator,
      Executor executor,
      Collection<Queue<PooledRequest<?>>> requestQueues) {
    this.pollCoordinator = pollCoordinator;
    this.executor = executor;
    this.requestQueues = requestQueues;
    this.submissionCounter = registry.counter("pooledRequestQueue.submitted");
  }

  void shutdown() {
    continueRunning.set(false);
  }

  @Override
  public void run() {
    while (continueRunning.get()) {
      processPartitions();
    }
  }

  void processPartitions() {
    try {
      boolean hadItems = false;
      pollCoordinator.reset();
      for (Queue<PooledRequest<?>> queue : requestQueues) {
        final PooledRequest<?> request = queue.poll();
        if (request != null) {
          hadItems = true;
          submissionCounter.increment();
          executor.execute(request);
        }
      }

      pollCoordinator.waitForItems(hadItems);
    } catch (Throwable t) {
      log.warn("Throwable during processPartitions", t);
    }
  }
}
