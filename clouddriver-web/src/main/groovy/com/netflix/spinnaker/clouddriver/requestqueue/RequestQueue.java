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

package com.netflix.spinnaker.clouddriver.requestqueue;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.requestqueue.pooled.PooledRequestQueue;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * RequestQueue.
 */
public interface RequestQueue {

  long DEFAULT_TIMEOUT_MILLIS = 60000;
  long DEFAULT_START_WORK_TIMEOUT_MILLIS = 10000;

  static RequestQueue forConfig(DynamicConfigService dynamicConfigService,
                                Registry registry,
                                RequestQueueConfiguration config) {
    if (!config.isEnabled()) {
      return noop();
    }

    return pooled(
      dynamicConfigService,
      registry,
      config.getStartWorkTimeoutMillis(),
      config.getTimeoutMillis(),
      config.getPoolSize()
    );
  }

  static RequestQueue noop() {
    return new NOOP();
  }

  static RequestQueue pooled(DynamicConfigService dynamicConfigService,
                             Registry registry,
                             int poolSize) {
    return pooled(
      dynamicConfigService,
      registry,
      DEFAULT_START_WORK_TIMEOUT_MILLIS,
      DEFAULT_TIMEOUT_MILLIS,
      poolSize
    );
  }

  static RequestQueue pooled(DynamicConfigService dynamicConfigService,
                             Registry registry,
                             long startWorkTimeoutMillis,
                             long timeoutMillis,
                             int poolSize) {
    return new PooledRequestQueue(
      dynamicConfigService,
      registry,
      startWorkTimeoutMillis,
      timeoutMillis,
      poolSize
    );
  }

  default long getDefaultTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS;
  }

  default long getDefaultStartWorkTimeoutMillis() {
    return DEFAULT_START_WORK_TIMEOUT_MILLIS;
  }

  default <T> T execute(String partition, Callable<T> operation) throws Throwable {
    return execute(partition, operation, getDefaultStartWorkTimeoutMillis(), getDefaultTimeoutMillis(), TimeUnit.MILLISECONDS);
  }

  <T> T execute(String partition, Callable<T> operation, long startWorkTimeout, long timeout, TimeUnit unit) throws Throwable;

  class NOOP implements RequestQueue {
    @Override
    public <T> T execute(String partition, Callable<T> operation, long startWorkTimeout, long timeout, TimeUnit unit) throws Throwable {
      return operation.call();
    }
  }
}
