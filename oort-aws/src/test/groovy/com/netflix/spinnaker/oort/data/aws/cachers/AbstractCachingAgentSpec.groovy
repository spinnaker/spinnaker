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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.oort.data.aws.cachers.AbstractInfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgent
import com.netflix.spinnaker.oort.model.CacheService
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractCachingAgentSpec extends Specification {

  static final String REGION = 'us-east-1'
  static final String ACCOUNT = 'test'

  abstract AbstractInfrastructureCachingAgent getCachingAgent()

  @Shared
  InfrastructureCachingAgent agent

  @Shared
  AmazonEC2 amazonEC2

  @Shared
  CacheService cacheService

  @Shared
  AmazonAutoScaling amazonAutoScaling

  @Shared
  AmazonElasticLoadBalancing amazonElasticLoadBalancing

  def setup() {
    agent = getCachingAgent()
    def amazonClientProvider = Mock(AmazonClientProvider)
    amazonEC2 = Mock(AmazonEC2)
    amazonAutoScaling = Mock(AmazonAutoScaling)
    amazonElasticLoadBalancing = Mock(AmazonElasticLoadBalancing)
    amazonClientProvider.getAmazonEC2(_, _) >> amazonEC2
    amazonClientProvider.getAutoScaling(_, _) >> amazonAutoScaling
    amazonClientProvider.getAmazonElasticLoadBalancing(_, _) >> amazonElasticLoadBalancing
    agent.amazonClientProvider = amazonClientProvider
    cacheService = Mock(CacheService)
    agent.cacheService = cacheService
  }
}
