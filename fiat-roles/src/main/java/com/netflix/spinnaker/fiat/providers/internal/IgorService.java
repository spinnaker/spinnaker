/*
 * Copyright 2019 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.providers.HealthTrackable;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class IgorService implements HealthTrackable, InitializingBean {
  private final IgorApi igorApi;

  @Autowired @Getter private ProviderHealthTracker healthTracker;

  @Value("${services.igor.enabled:true}")
  private Boolean igorEnabled;

  private AtomicReference<List<BuildService>> buildServicesCache = new AtomicReference<>();

  public IgorService(IgorApi igorApi) {
    this.igorApi = igorApi;
  }

  @Override
  public void afterPropertiesSet() {
    try {
      refreshBuildServices();
    } catch (Exception e) {
      log.warn("Cache initialization failed: ", e);
    }
  }

  public List<BuildService> getAllBuildServices() {
    return buildServicesCache.get();
  }

  @Scheduled(fixedDelayString = "${fiat.igorRefreshMs:30000}")
  public void refreshBuildServices() {
    if (igorEnabled) {
      buildServicesCache.set(igorApi.getBuildMasters());
    }
    healthTracker.success();
  }
}
