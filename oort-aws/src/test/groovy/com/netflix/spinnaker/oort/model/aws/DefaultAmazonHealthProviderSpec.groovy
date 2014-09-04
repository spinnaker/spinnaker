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

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceState
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.cachers.InstanceCachingAgent
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.ServerGroup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultAmazonHealthProviderSpec extends Specification {

  static final String REGION = 'us-west-1'
  static final String ACCOUNT = 'test'
  static final String INSTANCE_ID = 'i-12345'

  ServerGroup serverGroup = Stub(ServerGroup) {
    getRegion() >> REGION
  }

  @Subject
  DefaultAmazonHealthProvider provider

  CacheService cacheService

  def setup() {
    cacheService = Mock(CacheService)
    provider = new DefaultAmazonHealthProvider(cacheService: cacheService)
  }

  @Unroll("health is #expected if instance is #runningState")
  void "amazon health provider can only indicate Unknown or Down state"(InstanceCachingAgent.InstanceStateValue runningState, HealthState expected) {
    when:
    def result = provider.getHealth(ACCOUNT, serverGroup, INSTANCE_ID)

    then:
    1 * cacheService.retrieve(Keys.getInstanceKey(INSTANCE_ID, REGION), _) >> {
      Stub(Instance) {
        getState() >> runningState.buildInstanceState()
      }
    }

    and:
    result == new AwsInstanceHealth(type: DefaultAmazonHealthProvider.HEALTH_TYPE, instanceId: INSTANCE_ID, state: expected)

    where:
    runningState                                         | expected
    InstanceCachingAgent.InstanceStateValue.Pending      | HealthState.Down
    InstanceCachingAgent.InstanceStateValue.Running      | HealthState.Unknown
    InstanceCachingAgent.InstanceStateValue.ShuttingDown | HealthState.Down
    InstanceCachingAgent.InstanceStateValue.Stopped      | HealthState.Down
    InstanceCachingAgent.InstanceStateValue.Stopping     | HealthState.Down
    InstanceCachingAgent.InstanceStateValue.Terminated   | HealthState.Down
  }

  void "health is unknown for unknown instance"() {
    when:
    def result = provider.getHealth(ACCOUNT, serverGroup, INSTANCE_ID)

    then:
    1 * cacheService.retrieve(Keys.getInstanceKey(INSTANCE_ID, REGION), _) >> null

    and:
    result == new AwsInstanceHealth(type: DefaultAmazonHealthProvider.HEALTH_TYPE, instanceId: INSTANCE_ID, state: HealthState.Unknown)
  }
}
