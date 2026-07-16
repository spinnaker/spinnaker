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

package com.netflix.spinnaker.cats.test

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecution
import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import static com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState.CLEANING_UP
import static com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState.FAILED
import static com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState.NOT_RUNNING
import static com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState.RUNNING

class MockAgentLongRunningExecution implements LongRunningAgentExecution{
  private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  private LongRunningAgentExecutionState state = NOT_RUNNING
  private long stopDelay

  void setStopDelay(long stopDelay) {
    this.stopDelay = stopDelay
  }

  LongRunningAgentExecutionState getState() {
    return state
  }

  long getStopTimeoutMillis() {
    return 1000
  }

  CompletableFuture<Void> stopExecutingAndCleanup() {
    if (state != FAILED && state != RUNNING) {
      throw new IllegalStateException(String.format("cant cleanup from %s", state))
    }
    state = CLEANING_UP
    if (stopDelay == 0) {
      state = NOT_RUNNING
      return CompletableFuture.completedFuture()
    }
    else {
      CompletableFuture<Void> future = new CompletableFuture<>()
      scheduler.schedule(new FutureCompletionRunnable(future),stopDelay, TimeUnit.MILLISECONDS)
      return future;
    }

  }

  void executeAgent(Agent agent) {
    state = RUNNING
  }

  void fail() {
    state = FAILED
  }

  class FutureCompletionRunnable implements Runnable {
    CompletableFuture<Void> future
    FutureCompletionRunnable(CompletableFuture<Void> future) {
      this.future = future
    }
    void run() {
      MockAgentLongRunningExecution.this.state = NOT_RUNNING
      future.complete()
    }
  }
}
