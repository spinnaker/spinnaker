/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class Front50Service implements HealthTrackable, InitializingBean {

  private final Front50Api front50Api;

  @Autowired
  @Getter
  private ProviderHealthTracker healthTracker;

  private AtomicReference<List<Application>> applicationCache = new AtomicReference<>();
  private AtomicReference<List<ServiceAccount>> serviceAccountCache = new AtomicReference<>();

  public Front50Service(Front50Api front50Api) {
    this.front50Api = front50Api;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    try {
      // Initialize caches (also indicates service is healthy)
      refreshApplications();
      refreshServiceAccounts();
    } catch (Exception e) {
      log.warn("Cache prime failed: ", e);
    }
  }

  public List<Application> getAllApplicationPermissions() {
    return applicationCache.get();
  }

  public List<ServiceAccount> getAllServiceAccounts() {
    return serviceAccountCache.get();
  }

  private static void logFallback(String resource, Throwable cause) {
    String message = cause != null ? "Cause: " + cause.getMessage() : "";
    log.info("Falling back to {} cache. {}", resource, message);
  }


  @Scheduled(fixedDelayString = "${fiat.front50RefreshMs:30000}")
  public void refreshApplications() {
    applicationCache.set(
            front50Api.getAllApplicationPermissions()
    );
    healthTracker.success();
  }

  @Scheduled(fixedDelayString = "${fiat.front50RefreshMs:30000}")
  public void refreshServiceAccounts() {
    serviceAccountCache.set(
            front50Api.getAllServiceAccounts()
    );
    healthTracker.success();
  }
}
