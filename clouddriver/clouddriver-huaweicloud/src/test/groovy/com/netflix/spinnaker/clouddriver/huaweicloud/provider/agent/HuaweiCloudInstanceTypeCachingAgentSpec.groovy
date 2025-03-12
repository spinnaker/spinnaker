/*
 * Copyright 2020 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.huawei.openstack4j.openstack.ecs.v1.domain.Flavor
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys
import com.netflix.spinnaker.clouddriver.huaweicloud.client.HuaweiCloudClient
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject

class HuaweiCloudInstanceTypeCachingAgentSpec extends Specification {

  static final String AZ = 'cn-north-1a'
  static final String REGION = 'cn-north-1'
  static final String ACCOUNT_NAME = 'some-account-name'

  void "should add instance types on initial run"() {
    setup:
      def registry = new DefaultRegistry()
      def cloudClient = Mock(HuaweiCloudClient);
      def credentials = Mock(HuaweiCloudNamedAccountCredentials)
      credentials.cloudClient >> cloudClient
      credentials.name >> ACCOUNT_NAME
      credentials.regionToZones >> [(REGION): [AZ]]
      def ProviderCache providerCache = Mock(ProviderCache)

      @Subject
      HuaweiCloudInstanceTypeCachingAgent agent = new HuaweiCloudInstanceTypeCachingAgent(
          credentials, new ObjectMapper(), REGION)

      def flavorA = Flavor.builder()
         .name('c1.medium.1')
         .id('id-a')
         .build()

      def flavorB = Flavor.builder()
         .name('c1.medium.2')
         .id('id-b')
         .build()

      def keyA = Keys.getInstanceTypeKey(flavorA.id,
                                         ACCOUNT_NAME,
                                         REGION)

      def keyB = Keys.getInstanceTypeKey(flavorB.id,
                                         ACCOUNT_NAME,
                                         REGION)

    when:
      def cache = agent.loadData(providerCache)

    then:
      credentials.regionToZones[REGION] == [AZ]
      1 * cloudClient.getInstanceTypes(REGION, AZ) >> [flavorA, flavorB]
      with(cache.cacheResults.get(Keys.Namespace.INSTANCE_TYPES.ns)) { Collection<CacheData> cd ->
        cd.size() == 2
        cd.id.containsAll([keyA, keyB])
      }
  }
}
