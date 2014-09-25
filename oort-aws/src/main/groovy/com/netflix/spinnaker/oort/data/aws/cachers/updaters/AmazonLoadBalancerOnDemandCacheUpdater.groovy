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

package com.netflix.spinnaker.oort.data.aws.cachers.updaters

import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.data.aws.cachers.LoadBalancerCachingAgent
import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class AmazonLoadBalancerOnDemandCacheUpdater implements OnDemandCacheUpdater {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  ApplicationContext applicationContext

  @Override
  boolean handles(String type) {
    type == "AmazonLoadBalancer"
  }

  @Override
  void handle(Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName")) {
      return
    }
    if (!data.containsKey("account")) {
      return
    }
    if (!data.containsKey("region")) {
      return
    }

    def loadBalancerName = data.loadBalancerName as String
    def account = data.account as String
    def region = data.region as String

    def credentials = accountCredentialsProvider.getCredentials(account)
    if (!credentials) {
      return
    }
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      return
    }

    updateLoadBalancer loadBalancerName, credentials, region, (data.evict as boolean ?: false)
  }

  void updateLoadBalancer(String loadBalancerName, NetflixAmazonCredentials credentials, String region, boolean evict) {
    def loadBalancerCachingAgent = getWiredLoadBalancerCachingAgent(credentials, region)

    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(credentials, region, true)
    if (evict) {
      loadBalancerCachingAgent.removeMissingLoadBalancer(loadBalancerName, credentials.name, region)
    } else {
      def result = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(loadBalancerNames: [loadBalancerName]))
      if (result.loadBalancerDescriptions) {
        def loadBalancer = result.loadBalancerDescriptions[0]
        loadBalancerCachingAgent.loadNewLoadBalancer(loadBalancer, credentials.name, region)
      }
    }
  }

  private def getWiredLoadBalancerCachingAgent(NetflixAmazonCredentials credentials, String region) {
    (LoadBalancerCachingAgent) autowire(InfrastructureCachingAgentFactory.getLoadBalancerCachingAgent(credentials, region))
  }

  def autowire(obj) {
    applicationContext.autowireCapableBeanFactory.autowireBean obj
    obj
  }
}
