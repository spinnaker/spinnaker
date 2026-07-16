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
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString(
    of = {
      "agentType",
      "started",
      "stopped",
      "startTimeMillis",
      "lastReceivedEventTimeMillis",
      "lastProcessedEventBatchTimeMillis"
    })
class State {

  private final String agentType;
  private final ExecutorService executorService;
  private final KubernetesStreamingWatcherFactory factory;
  private final AtomicLong lastReceivedEventTimeMillis = new AtomicLong();
  private final AtomicLong lastProcessedEventBatchTimeMillis = new AtomicLong();
  private final long startTimeMillis = System.currentTimeMillis();
  private volatile boolean started = false;
  private volatile boolean stopped = false;

  State(
      String agentType,
      ExecutorService executorService,
      KubernetesStreamingWatcherFactory factory) {
    if (agentType == null || executorService == null || factory == null) {
      throw new IllegalStateException("agentType, executorService and factory must not be null");
    }

    this.agentType = agentType;
    this.executorService = executorService;
    this.factory = factory;
  }

  void start() {
    if (started) {
      throw new IllegalStateException("Already started");
    }
    started = true;
    log.info("Started streaming agent {}", agentType);
  }

  boolean stopAndWait(long timeoutMs) throws InterruptedException {
    if (stopped) {
      throw new IllegalStateException("Already stopped");
    }
    stopped = true;
    factory.stopAllRegisteredWatchers();
    executorService.shutdownNow();
    return executorService.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
  }

  KubernetesStreamingWatcherFactory getFactory() {
    return factory;
  }

  LongRunningAgentExecutionState getState(long readinessTimeoutMs, long livenessTimeoutMs) {
    if (!started) {
      log.info("Streaming agent {} not started yet, returning NOT_RUNNING state", agentType);
      return LongRunningAgentExecutionState.NOT_RUNNING;
    }

    if (stopped) {
      if (executorService.isTerminated()) {
        log.info(
            "Streaming agent {} has stopped and executor service is terminated, returning NOT_RUNNING state",
            agentType);
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
      log.warn(
          "Streaming agent {} FAILED liveness check. "
              + "Last processed batch: {} ago (limit: {}ms), "
              + "Last received event: {} ago, "
              + "Agent age: {}ms. ",
          agentType,
          lastProcessedEventBatchTimeMillis.get() > 0
              ? (now - lastProcessedEventBatchTimeMillis.get()) + "ms"
              : "NOT PROCESSED",
          livenessTimeoutMs,
          lastReceivedEventTimeMillis.get() > 0
              ? (now - lastReceivedEventTimeMillis.get()) + "ms"
              : "NOT PROCESSED",
          now - startTimeMillis);
      return LongRunningAgentExecutionState.FAILED;
    }
  }

  void updateLastReceivedEventTime() {
    long now = System.currentTimeMillis();
    if (lastReceivedEventTimeMillis.get() == 0) {
      log.info(
          "Streaming agent {}: FIRST event received ({}ms after start).",
          agentType,
          now - startTimeMillis);
    }
    lastReceivedEventTimeMillis.set(now);
  }

  long getLastReceivedEventTime() {
    return lastReceivedEventTimeMillis.get();
  }

  void updateLastProcessedEventBatchTime() {
    long now = System.currentTimeMillis();
    boolean isFirstBatch = lastProcessedEventBatchTimeMillis.get() == 0;
    lastProcessedEventBatchTimeMillis.set(now);
    if (isFirstBatch) {
      log.info(
          "Streaming agent {}: FIRST batch processed ({}ms after start).",
          agentType,
          now - startTimeMillis);
    }
  }

  long getLastProcessedEventBatchTime() {
    return lastProcessedEventBatchTimeMillis.get();
  }
}
