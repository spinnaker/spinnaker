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

import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.cachers.EddaLoadBalancerCachingAgent
import com.netflix.spinnaker.oort.model.*
import com.netflix.spinnaker.oort.model.edda.InstanceLoadBalancers
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class EddaLoadBalancerHealthProvider implements HealthProvider {
  @Autowired
  CacheService cacheService

  @Override
  Health getHealth(String account, ServerGroup serverGroup, String instanceId) {
    cacheService.retrieve(Keys.getInstanceHealthKey(instanceId, account, serverGroup.region, EddaLoadBalancerCachingAgent.PROVIDER_NAME), InstanceLoadBalancers) ?:
      new AwsInstanceHealth(type: InstanceLoadBalancers.HEALTH_TYPE, state: HealthState.Unknown, instanceId: instanceId)
  }
}
