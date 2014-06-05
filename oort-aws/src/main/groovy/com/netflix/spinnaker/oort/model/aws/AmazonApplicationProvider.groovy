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

package com.netflix.spinnaker.oort.model.aws

import com.netflix.spinnaker.oort.model.ApplicationProvider
import groovy.transform.CompileStatic
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class AmazonApplicationProvider implements ApplicationProvider {

  @Autowired
  CacheService<String, AmazonApplication> applicationCacheService

  @Override
  Set<AmazonApplication> getApplications() {
    def apps = applicationCacheService.map.keySet().collect { applicationCacheService.retrieve(it) }
    if (!apps) {
      apps = applicationCacheService.map.keySet().collect { applicationCacheService.retrieve(it) }
    }
    Collections.unmodifiableSet(apps as Set)
  }

  @Override
  AmazonApplication getApplication(String name) {
    applicationCacheService.getPointer(name) ? applicationCacheService.retrieve(name) : null
  }
}
