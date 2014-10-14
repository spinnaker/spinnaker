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

//@Component
class AmazonApplicationProvider { //implements ApplicationProvider {
//
//  @Autowired
//  CacheService cacheService
//
//  @Metric
//  Timer applications
//
//  @Metric
//  Timer applicationsByName
//
//  @Override
//  Set<AmazonApplication> getApplications() {
//    applications.time {
//      def appKeys = cacheService.keysByType(Namespace.APPLICATIONS)
//      def clusterKeys = cacheService.keysByType(Namespace.CLUSTERS)
//
//      def apps = (List<AmazonApplication>) rx.Observable.from(appKeys).flatMap {
//        rx.Observable.from(it).observeOn(Schedulers.computation()).map { String key ->
//          def app = cacheService.retrieve(key, AmazonApplication)
//          if (app) {
//            def appClusters = [:]
//            clusterKeys.findAll { it.startsWith("${Namespace.CLUSTERS}:${app.name}:") }.each {
//              def parts = it.split(':')
//              if (!appClusters.containsKey(parts[2])) {
//                appClusters[parts[2]] = new HashSet<>()
//              }
//              appClusters[parts[2]] << parts[3]
//            }
//            app.clusterNames = appClusters
//          }
//          app
//        }
//      }.reduce([], { apps, app ->
//        if (app) {
//          apps << app
//        }
//        apps
//      }).toBlocking().first()
//
//      Collections.unmodifiableSet(apps as Set)
//    }
//  }
//
//  @Override
//  AmazonApplication getApplication(String name) {
//    applicationsByName.time {
//      def app = cacheService.retrieve(Keys.getApplicationKey(name), AmazonApplication) ?: null
//      if (app) {
//        def clusters = [:]
//        cacheService.keysByType(Namespace.CLUSTERS).findAll { it.startsWith("${Namespace.CLUSTERS}:${name}:") }.each {
//          def parts = Keys.parse(it)
//          def account = parts.account
//          if (!clusters.containsKey(account)) {
//            clusters[account] = new HashSet()
//          }
//          clusters[account] << parts.cluster
//        }
//        app.clusterNames = clusters
//      }
//      app
//    }
//  }
}
