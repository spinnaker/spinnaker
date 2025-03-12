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

import static com.netflix.spinnaker.security.AuthenticatedRequest.allowAnonymous;

import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

public class DataLoader<T> implements HealthTrackable, ApplicationListener<ContextRefreshedEvent> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Supplier<List<T>> loadingFunction;
  private final ProviderHealthTracker healthTracker;

  private AtomicReference<List<T>> cache = new AtomicReference<>();

  public DataLoader(ProviderHealthTracker healthTracker, Supplier<List<T>> loadingFunction) {
    this.healthTracker = healthTracker;
    this.loadingFunction = loadingFunction;
  }

  protected List<T> getData() {
    List<T> data = loadData();
    cache.set(data);
    healthTracker.success();
    return data;
  }

  @Override
  public ProviderHealthTracker getHealthTracker() {
    return healthTracker;
  }

  protected List<T> getFallback(Throwable cause) throws Throwable {
    logFallback(getClass().getSimpleName(), cause);
    List<T> data = cache.get();
    if (data == null) {
      log.warn(
          "Failed loading data for {} and no fallback available",
          getClass().getSimpleName(),
          cause);
      throw cause;
    }
    return data;
  }

  private List<T> loadData() {
    List<T> data = allowAnonymous(loadingFunction::get);
    return Collections.unmodifiableList(data);
  }

  private void logFallback(String resource, Throwable cause) {
    String message = cause != null ? "Cause: " + cause.getMessage() : "";
    log.info("Falling back to {} cache. {}", resource, message);
  }

  protected void refreshCache() {
    try {
      getData();
    } catch (Exception e) {
      log.warn("Cache prime failed: ", e);
    }
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    refreshCache();
  }
}
