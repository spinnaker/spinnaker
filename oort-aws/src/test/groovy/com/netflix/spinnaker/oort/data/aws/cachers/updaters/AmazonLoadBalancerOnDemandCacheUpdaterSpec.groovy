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

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.data.aws.cachers.LoadBalancerCachingAgent
import com.netflix.spinnaker.oort.model.CacheService
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonLoadBalancerOnDemandCacheUpdaterSpec extends Specification {

  @Shared
  CacheService cacheService

  @Shared
  AmazonClientProvider amazonClientProvider

  @Shared
  NetflixAmazonCredentials credentials

  @Shared
  ConfigurableListableBeanFactory beanFactory = Stub(ConfigurableListableBeanFactory)

  @Subject
  AmazonLoadBalancerOnDemandCacheUpdater updater

  void setup() {
    GroovyMock(InfrastructureCachingAgentFactory, global: true)

    credentials = Stub(NetflixAmazonCredentials)
    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider)
    accountCredentialsProvider.getCredentials(_) >> credentials
    ApplicationContext ctx = Stub(ApplicationContext) {
      getAutowireCapableBeanFactory() >> beanFactory
    }

    cacheService = Mock(CacheService)
    amazonClientProvider = Stub(AmazonClientProvider)
    updater = new AmazonLoadBalancerOnDemandCacheUpdater(accountCredentialsProvider: accountCredentialsProvider, amazonClientProvider: amazonClientProvider, applicationContext: ctx)
  }

  void "should call amazon to get load balancer and invoke the load balancer caching agent ops"() {
    setup:
    def mockAgent = Mock(LoadBalancerCachingAgent)
    InfrastructureCachingAgentFactory.getLoadBalancerCachingAgent(credentials, region) >> mockAgent
    def loadBalancing = Mock(AmazonElasticLoadBalancing)
    amazonClientProvider.getAmazonElasticLoadBalancing(credentials, region, true) >> loadBalancing
    def loadBalancerDescription = new LoadBalancerDescription()
    credentials.getName() >> account

    when:
    updater.updateLoadBalancer(elbName, credentials, region, false)

    then:
    1 * loadBalancing.describeLoadBalancers(_) >> { DescribeLoadBalancersRequest req ->
      assert req.loadBalancerNames[0] == elbName
      new DescribeLoadBalancersResult(loadBalancerDescriptions: [loadBalancerDescription])
    }
    1 * mockAgent.loadNewLoadBalancer(loadBalancerDescription, account, region)

    where:
    account = "test"
    elbName = "foo--frontend"
    region = "us-east-1"
  }
}
