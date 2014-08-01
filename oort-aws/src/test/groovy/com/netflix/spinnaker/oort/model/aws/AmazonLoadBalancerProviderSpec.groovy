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

import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.Cluster
import com.netflix.spinnaker.oort.model.ClusterProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonLoadBalancerProviderSpec extends Specification {

  @Subject provider = new AmazonLoadBalancerProvider()

  @Shared
  CacheService cacheService

  def keys = [Keys.getLoadBalancerKey("app-frontend", "test", "us-west"), Keys.getLoadBalancerKey("app-frontend", "prod", "us-east")]

  def setup() {
    cacheService = Mock(CacheService)
    cacheService.keysByType(Keys.Namespace.LOAD_BALANCERS) >> keys
    provider.cacheService = cacheService
  }

  void "should retrieve all load balancers from cache"() {
    when:
    provider.getLoadBalancers()

    then:
    2 * cacheService.retrieve(_, LoadBalancerDescription) >>> [Mock(LoadBalancerDescription), Mock(LoadBalancerDescription)]
  }

  void "should retrieve lbs by account"() {
    when:
    provider.getLoadBalancers("test")

    then:
    1 * cacheService.retrieve(keys[0], _)
  }

  void "should pull lbs from clusters when asked for specifically"() {
    setup:
    def clusterProvider = Mock(ClusterProvider)
    provider.clusterProviders = [clusterProvider]

    when:
    provider.getLoadBalancers("prod", "app")

    then:
    1 * clusterProvider.getCluster(_, _, _) >> {
      def mock = Mock(Cluster)
      mock.getLoadBalancers() >> {
        [new AmazonLoadBalancer("name", "region")]
      }
      mock
    }
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("name", "prod", "region"), _)
  }

  void "should pull lbs from a cluster for a specific type"() {
    setup:
    def clusterProvider = Mock(ClusterProvider)
    provider.clusterProviders = [clusterProvider]
    def alb = new AmazonLoadBalancer("name", "region")

    when:
    def result = provider.getLoadBalancers("prod", "app", "aws")

    then:
    1 * clusterProvider.getCluster(_, _, _) >> {
      def mock = Mock(Cluster)
      mock.getType() >> "aws"
      mock.getLoadBalancers() >> [alb]
      mock
    }
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("name", "prod", "region"), _)
    alb.name == result[0].name
    alb.type == result[0].type
  }

  void "should pull lbs of a specific type and name, across regions"() {
    setup:
    def clusterProvider = Mock(ClusterProvider)
    provider.clusterProviders = [clusterProvider]
    def alb = new AmazonLoadBalancer("name", "region")
    def alb2 = new AmazonLoadBalancer("name", "region2")
    def alb3 = new AmazonLoadBalancer("name2", "region2")


    when:
    def result = provider.getLoadBalancer("prod", "app", "aws", "name")

    then:
    1 * clusterProvider.getCluster(_, _, _) >> {
      def mock = Mock(Cluster)
      mock.getType() >> "aws"
      mock.getLoadBalancers() >> [alb, alb2, alb3]
      mock
    }
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("name", "prod", "region"), _)
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("name", "prod", "region2"), _)
    2 == result.size()
    alb.region == result[0].region
    alb2.region == result[1].region
    !result.find { it.name == "name2" }
  }

  void "should pull a specific lb for a specific name and type and region"() {
    when:
    def result = provider.getLoadBalancer("prod", "app", "aws", "name", "region2")

    then:
    0 * cacheService.retrieve(Keys.getLoadBalancerKey("name", "prod", "region"), _)
    1 * cacheService.retrieve(Keys.getLoadBalancerKey("name", "prod", "region2"), _) >> Mock(LoadBalancerDescription)
    result instanceof AmazonLoadBalancer
    result.region == "region2"
  }
}
