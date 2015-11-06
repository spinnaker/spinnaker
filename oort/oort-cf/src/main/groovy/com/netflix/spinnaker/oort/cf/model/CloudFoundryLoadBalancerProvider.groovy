/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * @author Greg Turnquist
 */
@Component
class CloudFoundryLoadBalancerProvider implements LoadBalancerProvider<CloudFoundryLoadBalancer> {

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  @Override
  Map<String, Set<CloudFoundryLoadBalancer>> getLoadBalancers() {
    cloudFoundryResourceRetriever.loadBalancersByAccount
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancers(String account) {
    cloudFoundryResourceRetriever.loadBalancersByAccount[account]
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancers(String account, String cluster) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster]
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster]
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster].findAll{
      it.name == loadBalancerName
    } as Set<CloudFoundryLoadBalancer>
  }

  @Override
  CloudFoundryLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster].findAll{
      it.name == loadBalancerName
    } as Set<CloudFoundryLoadBalancer>
  }

  @Override
  Set<CloudFoundryLoadBalancer> getApplicationLoadBalancers(String application) {
    cloudFoundryResourceRetriever.loadBalancersByApplication[application]
  }
}
