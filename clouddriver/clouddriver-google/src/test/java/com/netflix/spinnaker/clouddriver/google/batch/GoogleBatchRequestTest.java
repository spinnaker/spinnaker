/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GoogleBatchRequestTest {
  private static final int CALLBACK_THREADS = 8;
  private static final int REQUESTS_PER_THREAD = 20_000;

  // execute() runs a stage's batches in parallel, and their callbacks queue the next stage's reads
  // into one GoogleBatchRequest.
  @Test
  void queueKeepsEveryRequestFromParallelCallbacks() throws Exception {
    GoogleBatchRequest nextStage = new GoogleBatchRequest(mock(Compute.class), "clouddriver");
    ComputeRequest<?> request = mock(ComputeRequest.class);
    JsonBatchCallback<?> callback = mock(JsonBatchCallback.class);
    CountDownLatch ready = new CountDownLatch(CALLBACK_THREADS);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService callbackThreads = Executors.newFixedThreadPool(CALLBACK_THREADS);
    try {
      List<Future<?>> callbacks = new ArrayList<>();
      for (int i = 0; i < CALLBACK_THREADS; i++) {
        callbacks.add(
            callbackThreads.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                    nextStage.queue(request, callback);
                  }
                  return null;
                }));
      }
      // Guards against a hang only; every thread waits on start before queueing.
      assertThat(ready.await(1, TimeUnit.MINUTES)).isTrue();
      start.countDown();
      for (Future<?> future : callbacks) {
        future.get();
      }
    } finally {
      callbackThreads.shutdownNow();
    }

    assertThat(nextStage.size()).isEqualTo(CALLBACK_THREADS * REQUESTS_PER_THREAD);
  }
}
