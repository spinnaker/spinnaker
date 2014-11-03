/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services

import groovy.transform.CompileStatic
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class CacheInvalidationService {
  private final List<CacheEnabledService> cacheEnabledServices

  @Autowired
  CacheInvalidationService(List<CacheEnabledService> cacheEnabledServices) {
    this.cacheEnabledServices = cacheEnabledServices
  }

  @PostConstruct
  void init() {
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate({
      invalidateAll()
    }, 0, 10, TimeUnit.SECONDS)
  }

  void invalidateAll() {
    for (service in cacheEnabledServices) {
      service.evict()
    }
  }
}
