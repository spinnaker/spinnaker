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
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import reactor.event.Event

import static reactor.event.selector.Selectors.object

@CompileStatic
class LoadBalancerCachingAgent extends AbstractInfrastructureCachingAgent {

  LoadBalancerCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownLoadBalancers = [:]

  void load() {
    log.info "$cachePrefix - Beginning Load Balancer Cache Load."

    reactor.on(object("newLoadBalancer"), this.&loadNewLoadBalancer)
    reactor.on(object("missingLoadBalancer"), this.&removeMissingLoadBalancer)

    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account.credentials, region)
    def loadBalancers = loadBalancing.describeLoadBalancers()
    def allLoadBalancers = loadBalancers.loadBalancerDescriptions.collectEntries { LoadBalancerDescription loadBalancerDescription -> [(loadBalancerDescription.loadBalancerName): loadBalancerDescription] }
    Map<String, Integer> loadBalancersThisRun = (Map<String, Integer>)allLoadBalancers.collectEntries { loadBalancerName, loadBalancer -> [(loadBalancerName): loadBalancer.hashCode()]}
    def newLoadBalancers = loadBalancersThisRun.specialSubtract(lastKnownLoadBalancers)
    def missingLoadBalancers = lastKnownLoadBalancers.keySet() - loadBalancersThisRun.keySet()

    if (newLoadBalancers) {
      log.info "$cachePrefix - Loading ${newLoadBalancers.size()} new load balancers"
      for (loadBalancerName in loadBalancersThisRun.keySet()) {
        LoadBalancerDescription loadBalancer = (LoadBalancerDescription)allLoadBalancers[loadBalancerName]
        reactor.notify("newLoadBalancer", Event.wrap(new LoadBalancerNotification(loadBalancer.loadBalancerName, loadBalancer, region)))
      }
    }
    if (missingLoadBalancers) {
      log.info "$cachePrefix - Removing ${missingLoadBalancers.size()} missing load balancers"
      for (loadBalancerName in missingLoadBalancers) {
        reactor.notify("missingLoadBalancer", Event.wrap(new LoadBalancerNotification(loadBalancerName, null, region)))
      }
    }
    if (!newLoadBalancers && !missingLoadBalancers) {
      log.info "$cachePrefix - Nothing new to process"
    }

    lastKnownLoadBalancers = loadBalancersThisRun
  }

  @Canonical
  static class LoadBalancerNotification {
    String loadBalancerName
    LoadBalancerDescription loadBalancerDescription
    String region
  }

  void loadNewLoadBalancer(Event<LoadBalancerNotification> event) {
    cacheService.put(Keys.getLoadBalancerKey(event.data.loadBalancerName, event.data.region), event.data.loadBalancerDescription)
  }

  void removeMissingLoadBalancer(Event<LoadBalancerNotification> event) {
    cacheService.free(Keys.getLoadBalancerKey(event.data.loadBalancerName, event.data.region))
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:elb]"
  }
}
