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

import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * This class makes and caches live calls to Clouddriver. In the event that Clouddriver is
 * unavailable, the cached data is returned in stead. Failed calls are logged with the Clouddriver
 * health tracker, which will turn unhealthy after X number of failed cache refreshes.
 */
@Slf4j
public class ClouddriverService implements HealthTrackable, InitializingBean {
  private final ClouddriverApi clouddriverApi;

  @Autowired @Getter private ProviderHealthTracker healthTracker;

  private AtomicReference<List<Application>> applicationCache = new AtomicReference<>();
  private AtomicReference<List<Account>> accountCache = new AtomicReference<>();

  public ClouddriverService(ClouddriverApi clouddriverApi) {
    this.clouddriverApi = clouddriverApi;
  }

  @Override
  public void afterPropertiesSet() {
    try {
      refreshAccounts();
      refreshApplications();
    } catch (Exception e) {
      log.warn("Cache initialization failed: ", e);
    }
  }

  public List<Account> getAccounts() {
    return accountCache.get();
  }

  public List<Application> getApplications() {
    return applicationCache.get();
  }

  @Scheduled(fixedDelayString = "${fiat.clouddriverRefreshMs:30000}")
  public void refreshAccounts() {
    accountCache.set(clouddriverApi.getAccounts());
    healthTracker.success();
  }

  @Scheduled(fixedDelayString = "${fiat.clouddriverRefreshMs:30000}")
  public void refreshApplications() {
    applicationCache.set(clouddriverApi.getApplications());
    healthTracker.success();
  }
}
