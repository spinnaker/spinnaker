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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class Front50Service implements HealthTrackable {

  private static final String GROUP_KEY = "front50Service";

  private final Front50Api front50Api;

  @Autowired
  @Getter
  private ProviderHealthTracker healthTracker;

  private AtomicReference<List<Application>> applicationCache = new AtomicReference<>();
  private AtomicReference<List<ServiceAccount>> serviceAccountCache = new AtomicReference<>();

  public Front50Service(Front50Api front50Api) {
    this.front50Api = front50Api;
  }

  public List<Application> getAllApplicationPermissions() {
    return new SimpleJava8HystrixCommand<>(
        GROUP_KEY,
        "getAllApplicationPermissions",
        () -> {
          applicationCache.set(front50Api.getAllApplicationPermissions());
          healthTracker.success();
          return applicationCache.get();
        },
        (Throwable cause) -> {
          log.warn("Falling back to application cache. Cause: " + cause.getMessage());
          List<Application> applications = applicationCache.get();
          if (applications == null) {
            throw new HystrixBadRequestException("Front50 is unavailable", cause);
          }
          return applications;
        }).execute();
  }

  public List<ServiceAccount> getAllServiceAccounts() {
    return new SimpleJava8HystrixCommand<>(
        GROUP_KEY,
        "getAccounts",
        () -> {
          serviceAccountCache.set(front50Api.getAllServiceAccounts());
          healthTracker.success();
          return serviceAccountCache.get();
        },
        (Throwable cause) -> {
          log.warn("Falling back to service account cache. Cause: " + cause.getMessage());
          List<ServiceAccount> serviceAccounts = serviceAccountCache.get();
          if (serviceAccounts == null) {
            throw new HystrixBadRequestException("Front50 is unavailable", cause);
          }
          return serviceAccounts;
        }).execute();
  }
}
