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

import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.config.edda.EddaApi
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.edda.InstanceLoadBalancerState
import com.netflix.spinnaker.oort.model.edda.InstanceLoadBalancers
import com.netflix.spinnaker.oort.model.edda.LoadBalancerInstance
import com.netflix.spinnaker.oort.model.edda.LoadBalancerInstanceState
import spock.lang.Shared

class EddaLoadBalancerCachingAgentSpec  extends AbstractCachingAgentSpec {
  @Shared
  EddaApi eddaApi

  @Override
  AbstractInfrastructureCachingAgent getCachingAgent() {
    eddaApi = Mock(EddaApi)
    new EddaLoadBalancerCachingAgent(new NetflixAmazonCredentials(name: ACCOUNT, edda: 'http://edda'), REGION, eddaApi)
  }

  void "load new health when new ones are available, remove missing ones, and do nothing when theres nothing new to process"() {
    setup:
    def health = new InstanceLoadBalancers(instanceId: 'i-12345', state: HealthState.Up, loadBalancers: [new InstanceLoadBalancerState('i-12345', 'lb1', 'InService', 'N/A', 'N/A')])
    def key = Keys.getInstanceHealthKey(health.instanceId, ACCOUNT, REGION, EddaLoadBalancerCachingAgent.PROVIDER_NAME)

    when:
    agent.load()

    then:
    1 * eddaApi.loadBalancerInstances() >> [new LoadBalancerInstanceState(name: 'lb1', instances: [new LoadBalancerInstance('i-12345', 'InService', 'N/A', 'N/A')])]
    1 * cacheService.put(key, health)

    when:
    agent.load()

    then:
    1 * eddaApi.loadBalancerInstances() >> []
    1 * cacheService.free(key)

    when:
    agent.load()

    then:
    1 * eddaApi.loadBalancerInstances() >> []
    0 * cacheService.free(_)
  }
}
