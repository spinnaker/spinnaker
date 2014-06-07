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
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonApplicationProvider implements ApplicationProvider {

  @Autowired
  CacheService<String, Object> cacheService

  @Override
  Set<AmazonApplication> getApplications() {
    def keys = cacheService.map.keySet()
    def apps = (List<AmazonApplication>) keys.findAll { it.startsWith("applications") }.collect { cacheService.retrieve(it) }
    for (app in apps) {
      def appClusters = [:]
      keys.findAll { it.startsWith("clusters:${app.name}") }.each {
        def parts = it.split(':')
        if (!appClusters.containsKey(parts[2])) {
          appClusters[parts[2]] = new HashSet<>()
        }
        appClusters[parts[2]] << parts[3]
      }
      app.clusterNames = appClusters
    }
    Collections.unmodifiableSet(apps as Set)
  }

  @Override
  AmazonApplication getApplication(String name) {
    def app = (AmazonApplication) cacheService.retrieve(name) ?: null
    if (app) {
      def clusters = [:]
      cacheService.map.keySet().findAll { it.startsWith("clusters:${name}") }.each {
        def parts = it.split(':')
        def clusterName = parts[1]
        def account = parts[2]
        if (!clusters.containsKey(account)) {
          clusters[account] = new HashSet()
        }
        clusters[account] << clusterName
      }
      app.clusterNames = clusters
    }
    app
  }
}
