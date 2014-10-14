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

package com.netflix.spinnaker.oort.provider.aws.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.model.Application
import com.netflix.spinnaker.oort.model.ApplicationProvider
import com.netflix.spinnaker.oort.model.Cluster
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.regex.Pattern

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.CLUSTERS

@Component
class CatsApplicationProvider implements ApplicationProvider {

  private static final Pattern CLUSTER_REGEX = Pattern.compile(/([^\/]+)\/(.*)/)

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  CatsApplicationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<Application> getApplications() {
    cacheView.getAll(APPLICATIONS.ns).collect this.&translate
  }

  @Override
  Application getApplication(String name) {
    translate(cacheView.get(APPLICATIONS.ns, name.toLowerCase()))
  }

  Application translate(CacheData cacheData) {
    String name = cacheData.id
    Map<String, String> attributes = objectMapper.convertValue(cacheData.attributes, CatsApplication.ATTRIBUTES)
    Map<String, String> clusterNames = cacheData.relationships[CLUSTERS.ns]?.findResults {
      def matcher = CLUSTER_REGEX.matcher(it)
      if (matcher.matches()) {
        [(matcher.group(1)): matcher.group(2)]
      } else {
        println "no match for $it"
        null
      }
    }?.collectEntries() ?: [:]
    new CatsApplication(name, attributes, clusterNames)
  }

  private static class CatsApplication implements Application {
    public static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<Map<String, String>>() {}
    final String name
    final Map<String, String> attributes
    final Map<String, String> clusterNames

    CatsApplication(String name, Map<String, String> attributes, Map<String, String> clusterNames) {
      this.name = name
      this.attributes = attributes
      this.clusterNames = clusterNames
    }
  }
}
