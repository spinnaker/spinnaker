/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class GoogleLoadBalancerProvider implements LoadBalancerProvider<GoogleLoadBalancer> {

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  @Override
  Map<String, Set<GoogleLoadBalancer>> getLoadBalancers() {
    Map<String, Map<String, List<GoogleLoadBalancer>>> networkLoadBalancerMap = googleResourceRetriever.networkLoadBalancerMap
    def loadBalancersByAccount = [:].withDefault { new HashSet<GoogleLoadBalancer>() }

    networkLoadBalancerMap.each { accountName, regionToLoadBalancersMap ->
      regionToLoadBalancersMap.each { region, loadBalancerList ->
        loadBalancersByAccount[accountName].addAll(loadBalancerList)
      }
    }

    loadBalancersByAccount
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancers(String account) {
    getLoadBalancers()[account]
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancers(String account, String cluster) {
    getLoadBalancers(account).findAll { loadBalancer ->
      Names.parseName(loadBalancer.name).cluster == cluster
    }
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    getLoadBalancers(account, cluster)
  }

  @Override
  Set<GoogleLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    getLoadBalancers(account, cluster).findAll { it.name == loadBalancerName }
  }

  @Override
  GoogleLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    Set<GoogleLoadBalancer> loadBalancerSet = getLoadBalancers(account, cluster)

    loadBalancerSet.find {
      it.name == loadBalancerName && it.region == region
    }
  }

  @Override
  Set<GoogleLoadBalancer> getApplicationLoadBalancers(String application) {
    Map<String, Map<String, List<GoogleLoadBalancer>>> networkLoadBalancerMap = googleResourceRetriever.networkLoadBalancerMap
    Set<GoogleLoadBalancer> applicationLoadBalancers = [] as Set<GoogleLoadBalancer>

    // Examine each load balancer. Include it in the returned list if either:
    //   - The name of the load balancer follows the naming-convention and begins with $applicationName-
    //   - The load balancer is associated with a server group in the application
    networkLoadBalancerMap.each { accountName, regionToLoadBalancersMap ->
      regionToLoadBalancersMap.each { region, loadBalancerList ->
        def loadBalancerListCopy = []

        loadBalancerList.each { loadBalancer ->
          // If the naming-convention is followed, add it to the list of matches.
          if (Names.parseName(loadBalancer.name).app == application) {
            // Clone the load balancer so the original is not mutated when we prune the server groups.
            applicationLoadBalancers << new GoogleLoadBalancer(loadBalancer)
          } else {
            loadBalancerListCopy << loadBalancer

            // Check each of the load balancer's server groups to see if they are in this application.
            if (loadBalancer.serverGroups.find { loadBalancerServerGroup ->
              Names.parseName(loadBalancerServerGroup.name).app == application
            }) {
              // Clone the load balancer so the original is not mutated when we prune the server groups.
              applicationLoadBalancers << new GoogleLoadBalancer(loadBalancer)
            }
          }
        }
      }
    }

    // Filter out server groups not in this application from the returned load balancers.
    // Remove instanceNames key/value since we don't need to return it.
    applicationLoadBalancers.each { loadBalancer ->
      loadBalancer.serverGroups = loadBalancer.serverGroups.findAll { loadBalancerServerGroup ->
        Names.parseName(loadBalancerServerGroup.name).app == application
      }

      loadBalancer.remove("instanceNames")
    }

    applicationLoadBalancers
  }

}
