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

package com.netflix.spinnaker.oort.controllers.ext

import com.netflix.spinnaker.oort.data.aws.Keys.Namespace
import com.netflix.spinnaker.oort.model.Application
import com.netflix.spinnaker.oort.model.ApplicationProvider
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RestController
@RequestMapping(value = "/applications", produces = "application/appsref+json")
class LowResApplicationsRefController {
  static final ExecutorService executorService = Executors.newFixedThreadPool(4)

  @Autowired
  List<ApplicationProvider> applicationProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  CacheService cacheService

  @RequestMapping(method = RequestMethod.GET)
  def list() {
    def apps = (List<Application>) applicationProviders.collectMany {
      it.applications ?: []
    }
    def keys = cacheService.keys()
    def callables = []
    for (application in apps) {
      callables << c.curry(application, keys)
    }
    executorService.invokeAll(callables)*.get()
  }

  Closure<LowResApplicationViewModel> c = { Application application, Set<String> keys ->
    def app = new LowResApplicationViewModel(name: application.name, clusters: [])
    app.clusters = (List<LowResClusterViewModel>)application.clusterNames.collectMany { account, clusterNames ->
      clusterNames.collect { clusterName ->
        def serverGroups = [:]
        def key = "${Namespace.SERVER_GROUPS}:${clusterName}:${account}:".toString()
        keys.findAll { it.startsWith(key) }.each {
          def parts = it.split(':')
          def region = parts[3]
          def serverGroup = parts[4]
          if (!serverGroups.containsKey(region)) {
            serverGroups[region] = []
          }
          ((List<String>) serverGroups[region]) << serverGroup
        }
        new LowResClusterViewModel(name: clusterName, account: account, serverGroups: serverGroups)
      } as List<LowResClusterViewModel>
    }
    app
  }

  static class LowResApplicationViewModel {
    String name
    List<LowResClusterViewModel> clusters
  }

  static class LowResClusterViewModel {
    String name
    String account
    Map<String, List<String>> serverGroups
  }
}
