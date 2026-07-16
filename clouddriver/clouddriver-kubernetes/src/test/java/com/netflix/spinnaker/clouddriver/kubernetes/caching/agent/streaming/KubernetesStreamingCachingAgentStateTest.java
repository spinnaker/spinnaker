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

import com.netflix.spinnaker.cats.agent.LongRunningAgentExecutionState;
import java.util.concurrent.ExecutorService;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KubernetesStreamingCachingAgentStateTest {

  private State state;

  private ExecutorService executor;
  private KubernetesStreamingWatcherFactory kubernetesWatcherFactory;

  @BeforeEach
  void setUp() {
    executor = Mockito.mock(ExecutorService.class);
    kubernetesWatcherFactory = Mockito.mock(KubernetesStreamingWatcherFactory.class);
    state = new State("test-account", executor, kubernetesWatcherFactory, new OkHttpClient());
  }

  @Test
  void testStartTwice() {
    state.start();
    assertThatThrownBy(state::start).hasMessageContaining("Already started");
  }

  @Test
  void testStatus() throws InterruptedException {
    // new state is not started
    assertThat(state.getState(0, 0)).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);

    state.start();

    // it's running within the readiness timeout, even if the last event time is 0
    assertThat(state.getState(10_000, 10_000)).isEqualTo(LongRunningAgentExecutionState.RUNNING);

    // it's failed if the last event time is 0 and the readiness timeout is 0
    assertThat(state.getState(0, 0)).isEqualTo(LongRunningAgentExecutionState.FAILED);

    // it's running if the last processed event time is no more than the liveness timeout
    state.updateLastProcessedEventBatchTime();
    assertThat(state.getState(0, 10_000)).isEqualTo(LongRunningAgentExecutionState.RUNNING);

    // stop
    state.stopAndWait(0);
    Mockito.verify(kubernetesWatcherFactory, Mockito.times(1)).stopAllRegisteredWatchers();
    Mockito.verify(executor, Mockito.times(1)).shutdownNow();

    // CLEANING_UP state is returned if the executor is not terminated yet
    Mockito.when(executor.isTerminated()).thenReturn(false);
    assertThat(state.getState(0, 0)).isEqualTo(LongRunningAgentExecutionState.CLEANING_UP);

    // NOT_RUNNING state is returned if the executor is terminated
    Mockito.when(executor.isTerminated()).thenReturn(true);
    assertThat(state.getState(0, 0)).isEqualTo(LongRunningAgentExecutionState.NOT_RUNNING);
  }
}
