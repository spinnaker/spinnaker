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

import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class State {

  private final ExecutorService executorService;
  private final KubernetesInformerFactory factory;
  private final AtomicLong lastReceivedEventTimeMillis = new AtomicLong();
  private final AtomicLong lastProcessedEventBatchTimeMillis = new AtomicLong();
  private final long startTimeMillis = System.currentTimeMillis();
  private volatile boolean started = false;
  private volatile boolean stopped = false;

  State(ExecutorService executorService, KubernetesInformerFactory factory) {
    if (executorService == null || factory == null) {
      throw new IllegalStateException("ExecutorService or factory is null");
    }

    this.executorService = executorService;
    this.factory = factory;
  }

  void start() {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    started = true;
  }

  boolean stopAndWait(long timeoutMs) throws InterruptedException {
    if (stopped) {
      throw new IllegalStateException("Already stopped");
    }
    stopped = true;
    factory.stopAllRegisteredInformers(false);
    executorService.shutdownNow();
    return executorService.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
  }

  KubernetesInformerFactory getFactory() {
    return factory;
  }

  LongRunningAgentExecutionState getState(long readinessTimeoutMs, long livenessTimeoutMs) {
    if (!started) {
      return LongRunningAgentExecutionState.NOT_RUNNING;
    }

    if (stopped) {
      if (executorService.isTerminated()) {
        return LongRunningAgentExecutionState.NOT_RUNNING;
      } else {
        return LongRunningAgentExecutionState.CLEANING_UP;
      }
    }

    long now = System.currentTimeMillis();
    boolean waitingForSync = startTimeMillis + readinessTimeoutMs > now;
    if (waitingForSync) {
      return LongRunningAgentExecutionState.RUNNING;
    }

    if (lastProcessedEventBatchTimeMillis.get() + livenessTimeoutMs > now) {
      return LongRunningAgentExecutionState.RUNNING;
    } else {
      return LongRunningAgentExecutionState.FAILED;
    }
  }

  void updateLastReceivedEventTime() {
    long now = System.currentTimeMillis();
    lastReceivedEventTimeMillis.set(now);
  }

  long getLastReceivedEventTime() {
    return lastReceivedEventTimeMillis.get();
  }

  void updateLastProcessedEventBatchTime() {
    long now = System.currentTimeMillis();
    lastProcessedEventBatchTimeMillis.set(now);
  }

  long getLastProcessedEventBatchTime() {
    return lastProcessedEventBatchTimeMillis.get();
  }
}
