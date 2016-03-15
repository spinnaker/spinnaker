/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.test;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A listener that allows tests to wait for a job to finish before making
 * wild assertions.
 * <p>
 * If you just register this bean in the application context it should get
 * picked up by Orca and attached to whatever jobs you run. Keep a handle on
 * it and use `await()` to make sure the job completes before your assertions
 * fire. If you need to run the job more than once use `reset()` in between.
 */
@Component
public class JobCompletionListener implements ExecutionListener, Ordered {

  public static final int DEFAULT_TIMEOUT_SECONDS = 1;

  private CountDownLatch latch = new CountDownLatch(1);

  @Override
  public void afterExecution(Persister persister,
                             Execution execution,
                             ExecutionStatus executionStatus,
                             boolean wasSuccessful) {
    latch.countDown();
  }

  public void await() throws InterruptedException {
    await(DEFAULT_TIMEOUT_SECONDS, SECONDS);
  }

  public void await(long timeout, TimeUnit unit) throws InterruptedException {
    latch.await(timeout, unit);
  }

  public synchronized void reset() {
    while (latch.getCount() > 0) latch.countDown();
    latch = new CountDownLatch(1);
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
