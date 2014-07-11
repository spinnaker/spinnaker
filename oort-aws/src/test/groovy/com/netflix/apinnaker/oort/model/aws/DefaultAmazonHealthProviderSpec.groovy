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

package com.netflix.apinnaker.oort.model.aws

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceState
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.ServerGroup
import com.netflix.spinnaker.oort.model.aws.AwsInstanceHealth
import com.netflix.spinnaker.oort.model.aws.DefaultAmazonHealthProvider
import spock.lang.Shared
import spock.lang.Specification

class DefaultAmazonHealthProviderSpec extends Specification {

  @Shared
  DefaultAmazonHealthProvider provider

  @Shared
  CacheService cacheService

  def setup() {
    provider =  new DefaultAmazonHealthProvider()
    cacheService = Mock(CacheService)
    provider.cacheService = cacheService
  }

  void "healthy if instance is running"() {
    setup:
    def region = "us-west-1"
    def serverGroup = Mock(ServerGroup)
    serverGroup.getRegion() >> region

    when:
    def result = provider.getHealth("test", serverGroup, "i-123456")

    then:
    result == new AwsInstanceHealth(type: "Amazon", id: "i-123456", state: HealthState.Up)

    and:
    1 * cacheService.retrieve(Keys.getInstanceKey("i-123456", region), _) >> {
      Mock(Instance) {
        getState() >> new InstanceState().withCode(16)
      }
    }
  }
}
