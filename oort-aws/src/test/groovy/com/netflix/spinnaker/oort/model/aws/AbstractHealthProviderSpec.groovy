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
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthProvider
import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.ServerGroup
import spock.lang.Specification
import spock.lang.Subject

abstract class AbstractHealthProviderSpec extends Specification {
  static final String ACCOUNT = 'test'
  static final String REGION = 'us-east-1'
  static final String INSTANCE_ID = 'i-12345'

  @Subject
  HealthProvider provider
  CacheService cacheService = Mock(CacheService)
  ServerGroup serverGroup = Stub(ServerGroup) {
    getRegion() >> REGION
  }

  String key = Keys.getInstanceHealthKey(INSTANCE_ID, ACCOUNT, REGION, getHealthKeyProviderName())

  abstract String getHealthKeyProviderName()
  abstract Class getHealthType()
  abstract String getHealthTypeName()
  abstract HealthProvider createProvider()
  abstract Health createCachedHealth()

  def setup() {
    provider = createProvider()
  }

  def 'element is returned from the cache if present'() {
    setup:
    def expected = createCachedHealth()

    when:
    def health = provider.getHealth(ACCOUNT, serverGroup, INSTANCE_ID)

    then:
    1 * cacheService.retrieve(key, healthType) >> expected
    health == expected
  }

  def 'if no element present, unknown health status is returned'() {
    def expected = new AwsInstanceHealth(instanceId: INSTANCE_ID, type: healthTypeName, state: HealthState.Unknown)

    when:
    def health = provider.getHealth(ACCOUNT, serverGroup, INSTANCE_ID)

    then:
    1 * cacheService.retrieve(key, healthType) >> null
    health == expected
  }

}
