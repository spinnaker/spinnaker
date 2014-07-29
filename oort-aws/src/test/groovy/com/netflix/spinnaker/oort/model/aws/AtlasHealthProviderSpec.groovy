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
import com.netflix.spinnaker.oort.data.aws.cachers.AtlasHealthCachingAgent
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.model.aws.AtlasHealthProvider
import spock.lang.Specification

class AtlasHealthProviderSpec extends Specification {

  def mockCacheService = Mock(CacheService)
  def provider = new AtlasHealthProvider(cacheService: mockCacheService)

  def serverGroupName = "kato-main-v000"
  def region = "us-east-1"
  def serverGroup = new AmazonServerGroup(serverGroupName, "aws", region)
  def instanceHealthKey = Keys.getInstanceHealthKey("i-12345", "test", region, AtlasHealthCachingAgent.PROVIDER_NAME)

  void "should retrieved health from cache"() {
    when:
    def result = provider.getHealth("test", serverGroup, "i-12345")

    then:
    result instanceof Health
    result.id == "i-12345"
    result.state == healthState
    result.state.isHealthy() == isHealthy

    and:
    1 * mockCacheService.retrieve(instanceHealthKey, _) >> cachedHealth

    where:
    cachedHealth                      | healthState         | isHealthy
    [id: "i-12345", isHealthy: true]  | HealthState.Up      | true
    [id: "i-12345", isHealthy: false] | HealthState.Down    | false
    null                              | HealthState.Unknown | false
  }
}
