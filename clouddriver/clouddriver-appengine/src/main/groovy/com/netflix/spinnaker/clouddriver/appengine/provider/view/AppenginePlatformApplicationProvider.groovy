/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.AppenginePlatformApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AppenginePlatformApplicationProvider {
  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  AppenginePlatformApplication getPlatformApplication(String project) {
    def platformApplicationKey = Keys.getPlatformApplicationKey(project)
    def platformApplicationData = cacheView.get(Keys.Namespace.PLATFORM_APPLICATIONS.ns, platformApplicationKey)

    if (platformApplicationData) {
      return objectMapper.convertValue(platformApplicationData.attributes.platformApplication, AppenginePlatformApplication)
    } else {
      return null
    }
  }
}
