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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.Keys
import groovy.transform.CompileStatic

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract

@CompileStatic
class LoadBalancerCachingAgent extends AbstractInfrastructureCachingAgent {

  LoadBalancerCachingAgent(NetflixAmazonCredentials account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownLoadBalancers = [:]

  void load() {
    log.info "$cachePrefix - Beginning Load Balancer Cache Load."

    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account, region)
    def loadBalancers = loadBalancing.describeLoadBalancers()
    def allLoadBalancers = loadBalancers.loadBalancerDescriptions.collectEntries { LoadBalancerDescription loadBalancerDescription -> [(loadBalancerDescription.loadBalancerName): loadBalancerDescription] }
    Map<String, Integer> loadBalancersThisRun = (Map<String, Integer>)allLoadBalancers.collectEntries { loadBalancerName, loadBalancer -> [(loadBalancerName): loadBalancer.hashCode()]}
    Map<String, Integer> newLoadBalancers = specialSubtract(loadBalancersThisRun, lastKnownLoadBalancers)
    Set<String> missingLoadBalancers = new HashSet<String>(lastKnownLoadBalancers.keySet())
    missingLoadBalancers.removeAll(loadBalancersThisRun.keySet())

    if (newLoadBalancers) {
      log.info "$cachePrefix - Loading ${newLoadBalancers.size()} new load balancers"
      for (loadBalancerName in loadBalancersThisRun.keySet()) {
        LoadBalancerDescription loadBalancer = (LoadBalancerDescription)allLoadBalancers[loadBalancerName]
        loadNewLoadBalancer(loadBalancer, account.name, region)
      }
    }
    if (missingLoadBalancers) {
      log.info "$cachePrefix - Removing ${missingLoadBalancers.size()} missing load balancers"
      for (loadBalancerName in missingLoadBalancers) {
        removeMissingLoadBalancer(loadBalancerName, account.name, region)
      }
    }
    if (!newLoadBalancers && !missingLoadBalancers) {
      log.info "$cachePrefix - Nothing new to process"
    }

    lastKnownLoadBalancers = loadBalancersThisRun
  }

  void loadNewLoadBalancer(LoadBalancerDescription loadBalancerDescription, String account, String region) {
    cacheService.put(Keys.getLoadBalancerKey(loadBalancerDescription.loadBalancerName, account, region), loadBalancerDescription)
  }

  void removeMissingLoadBalancer(String loadBalancerName, String account, String region) {
    cacheService.free(Keys.getLoadBalancerKey(loadBalancerName, account, region))
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:elb]"
  }
}
