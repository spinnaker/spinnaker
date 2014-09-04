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

import com.netflix.spinnaker.oort.data.aws.cachers.EddaLoadBalancerCachingAgent
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthProvider
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.edda.InstanceLoadBalancerState
import com.netflix.spinnaker.oort.model.edda.InstanceLoadBalancers

class EddaLoadBalancerHealthProviderSpec extends AbstractHealthProviderSpec {
  @Override
  String getHealthKeyProviderName() {
    EddaLoadBalancerCachingAgent.PROVIDER_NAME
  }

  @Override
  Class getHealthType() {
    InstanceLoadBalancers
  }

  @Override
  String getHealthTypeName() {
    InstanceLoadBalancers.HEALTH_TYPE
  }

  @Override
  HealthProvider createProvider() {
    return new EddaLoadBalancerHealthProvider(cacheService: cacheService)
  }

  @Override
  Health createCachedHealth() {
    new InstanceLoadBalancers(INSTANCE_ID, HealthState.Up, [
      new InstanceLoadBalancerState(instanceId: INSTANCE_ID, loadBalancerName: 'lb1', state: 'InService')
    ])
  }
}
