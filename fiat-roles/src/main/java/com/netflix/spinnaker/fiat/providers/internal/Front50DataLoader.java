/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * This class makes and caches live calls to front50. In the event that front50 is unavailable, the
 * cached data is returned in stead. Failed calls are logged with the front50 health tracker, which
 * will turn unhealthy after X number of failed cache refreshes.
 */
public class Front50DataLoader<T> extends DataLoader<T> {

  public Front50DataLoader(ProviderHealthTracker healthTracker, Supplier<List<T>> loadingFunction) {
    super(healthTracker, loadingFunction);
  }

  @Override
  @CircuitBreaker(name = "front50", fallbackMethod = "getFallback")
  @Retry(name = "front50", fallbackMethod = "getFallback")
  public List<T> getData() {
    return super.getData();
  }

  @Override
  @Scheduled(fixedDelayString = "${fiat.front50-refresh-ms:30000}")
  protected void refreshCache() {
    super.refreshCache();
  }
}
