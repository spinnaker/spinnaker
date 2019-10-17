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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("request-queue")
public class RequestQueueConfiguration {
  private boolean enabled = false;
  private long startWorkTimeoutMillis = RequestQueue.DEFAULT_START_WORK_TIMEOUT_MILLIS;
  private long timeoutMillis = RequestQueue.DEFAULT_TIMEOUT_MILLIS;
  private int poolSize = 10;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getStartWorkTimeoutMillis() {
    return startWorkTimeoutMillis;
  }

  public void setStartWorkTimeoutMillis(long startWorkTimeoutMillis) {
    this.startWorkTimeoutMillis = startWorkTimeoutMillis;
  }

  public long getTimeoutMillis() {
    return timeoutMillis;
  }

  public void setTimeoutMillis(long timeoutMillis) {
    this.timeoutMillis = timeoutMillis;
  }

  public int getPoolSize() {
    return poolSize;
  }

  public void setPoolSize(int poolSize) {
    this.poolSize = poolSize;
  }
}
