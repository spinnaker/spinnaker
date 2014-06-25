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

import com.codahale.metrics.Timer
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Keys.Namespace
import com.netflix.spinnaker.oort.model.ApplicationProvider
import com.netflix.spinnaker.oort.model.CacheService
import com.ryantenney.metrics.annotation.Metric
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.schedulers.Schedulers

@Component
class AmazonApplicationProvider implements ApplicationProvider {

  @Autowired
  CacheService cacheService

  @Metric
  Timer applications

  @Metric
  Timer applicationsByName



  @Override
  Set<AmazonApplication> getApplications() {
    applications.time {
      def appKeys = cacheService.keysByType(Namespace.APPLICATIONS)
      def clusterKeys = cacheService.keysByType(Namespace.CLUSTERS)

      def apps = (List<AmazonApplication>)rx.Observable.from(appKeys).flatMap {
        rx.Observable.from(it).observeOn(Schedulers.computation()).map { String key ->
          def app = (AmazonApplication)cacheService.retrieve(key)
          if (app) {
            def appClusters = [:]
            clusterKeys.findAll { it.startsWith("${Namespace.CLUSTERS}:${app.name}:") }.each {
              def parts = it.split(':')
              if (!appClusters.containsKey(parts[2])) {
                appClusters[parts[2]] = new HashSet<>()
              }
              app.clusterNames = appClusters
            }
            app
          }
        }.reduce([], { apps, app ->
          if (app) {
            apps << app
          }
          apps
        }).toBlockingObservable().first()

      }
      Collections.unmodifiableSet(apps as Set)
    }
  }

  @Override
  AmazonApplication getApplication(String name) {
    applicationsByName.time {
      def app = (AmazonApplication) cacheService.retrieve(Keys.getApplicationKey(name)) ?: null
      if (app) {
        def clusters = [:]
        cacheService.keysByType(Namespace.CLUSTERS).findAll { it.startsWith("${Namespace.CLUSTERS}:${name}:") }.each {
          def parts = it.split(':')
          def account = parts[2]
          def clusterName = parts[3]
          if (!clusters.containsKey(account)) {
            clusters[account] = new HashSet()
          }
          app.clusterNames = clusters
        }
      }
      app
    }
  }
}
