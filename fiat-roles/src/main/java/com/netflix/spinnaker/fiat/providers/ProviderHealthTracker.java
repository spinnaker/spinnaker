/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.fiat.providers;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicLong;

public class ProviderHealthTracker {

  /**
   * Maximum age of stale data before this instance goes unhealthy.
   */
  private final long maximumStalenessTimeMs;
  private AtomicLong lastSuccessfulUpdateTimeMs = new AtomicLong(-1);

  public ProviderHealthTracker(long maximumStalenessTimeMs) {
    this.maximumStalenessTimeMs = maximumStalenessTimeMs;
  }

  public void success() {
    lastSuccessfulUpdateTimeMs.set(System.currentTimeMillis());
  }

  public boolean isProviderHealthy() {
    return lastSuccessfulUpdateTimeMs.get() != -1 && getStaleness() < maximumStalenessTimeMs;
  }

  private long getStaleness() {
    if (lastSuccessfulUpdateTimeMs.get() == -1) {
      return -1;
    }
    return System.currentTimeMillis() - lastSuccessfulUpdateTimeMs.get();
  }

  public HealthView getHealthView() {
    return new HealthView();
  }

  @Data
  class HealthView {
    boolean providerHealthy = ProviderHealthTracker.this.isProviderHealthy();
    long msSinceLastSuccess = ProviderHealthTracker.this.getStaleness();
    long lastSuccessfulUpdateTime = ProviderHealthTracker.this.lastSuccessfulUpdateTimeMs.get();
    long maximumStalenessTimeMs = ProviderHealthTracker.this.maximumStalenessTimeMs;
  }
}
