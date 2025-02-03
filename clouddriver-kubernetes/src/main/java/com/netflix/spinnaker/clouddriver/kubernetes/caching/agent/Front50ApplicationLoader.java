/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.Front50Application;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * we could have a conditional on both kubernetes.cache.checkApplicationInFront50 and
 * services.front50.enabled properties. But if the former is enabled and the latter is not, that
 * means the downstream clients relying on this will fail, since the cache will be empty. So we
 * explicitly leave out the front50 conditional and log an error message in the refreshCache()
 * stating the same. We could have logged the error message in the downstream clients if if couldn't
 * find the front50ApplicationLoader bean but that can be extremely noisy.
 */
@Slf4j
@Component
@ConditionalOnProperty("kubernetes.cache.checkApplicationInFront50")
public class Front50ApplicationLoader {

  @Nullable private final Front50Service front50Service;
  private AtomicReference<Set<String>> cache;

  Front50ApplicationLoader(@Nullable Front50Service front50Service) {
    this.front50Service = front50Service;
    this.cache = new AtomicReference<>(Collections.emptySet());
  }

  public Set<String> getData() {
    return cache.get();
  }

  @Scheduled(
      fixedDelayString = "${kubernetes.cache.refreshFront50ApplicationsCacheIntervalInMs:60000}")
  protected void refreshCache() {
    try {
      log.info("refreshing front50 applications cache");
      if (front50Service == null) {
        log.info("front50 is disabled, cannot fetch applications");
        return;
      }
      Set<Front50Application> response =
          AuthenticatedRequest.allowAnonymous(
              () -> Retrofit2SyncCall.execute(front50Service.getAllApplicationsUnrestricted()));
      Set<String> applicationsKnownToFront50 =
          response.stream().map(Front50Application::getName).collect(Collectors.toSet());
      log.info("received {} applications from front50", applicationsKnownToFront50.size());
      cache.set(applicationsKnownToFront50);
    } catch (Exception e) {
      log.warn("failed to update application cache with new front50 data. Error: ", e);
    }
  }
}
