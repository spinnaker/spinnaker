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

package com.netflix.spinnaker.clouddriver.titus.caching.providers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.model.Application
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.model.TitusApplication
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.SERVER_GROUPS

@Component
class TitusApplicationProvider implements ApplicationProvider {
  TitusCloudProvider titusCloudProvider
  private final Cache cacheView

  @Autowired
  TitusApplicationProvider(TitusCloudProvider titusCloudProvider, Cache cacheView) {
    this.titusCloudProvider = titusCloudProvider
    this.cacheView = cacheView
  }

  @Override
  Set<Application> getApplications(boolean expand) {
    String allTitusGlob = "${titusCloudProvider.id}:*"

    //ignoring expand since we are deriving existence of the app by presence of server groups
    // rather than the application cacheData which is not reliably updated or evicted
    Map<String, Map<String, Set<String>>> appClusters = getAppClustersByAccount(allTitusGlob)
    return appClusters.findResults {translate(it.key, appClusters) }
  }

  @Override
  Application getApplication(String name) {
    name = name.toLowerCase()
    String glob = Keys.getServerGroupV2Key("${name}*", "*", "*", "*")
    return translate(name, getAppClustersByAccount(glob))
  }

  Application translate(String name, Map<String, Map<String, Set<String>>> appClustersByAccount) {
    Map<String, Set<String>> clusterNames = appClustersByAccount.get(name)
    if (!clusterNames) {
      return null
    }
    Map<String, String> attributes = Map.of("name", name)
    return new TitusApplication(name, clusterNames, attributes)
  }

  private Map<String, Map<String, Set<String>>> getAppClustersByAccount(String glob) {
    // app -> account -> [clusterName..]
    Map<String, Map<String, Set<String>>> appClustersByAccount = [:].withDefault { [:].withDefault { [] as Set } }
    Collection<String> serverGroupKeys = cacheView.filterIdentifiers(SERVER_GROUPS.ns, glob)
    for (String key : serverGroupKeys) {
      Map<String, String> sg = Keys.parse(key)
      if (sg && sg.application && sg.cluster && sg.account) {
        appClustersByAccount.get(sg.application).get(sg.account).add(sg.cluster)
      }
    }
    return appClustersByAccount
  }
}
