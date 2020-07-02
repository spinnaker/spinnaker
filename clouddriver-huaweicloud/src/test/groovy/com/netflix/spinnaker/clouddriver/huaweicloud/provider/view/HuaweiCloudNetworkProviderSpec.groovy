/*
 * Copyright 2020 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.huawei.openstack4j.openstack.vpc.v1.domain.Vpc
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class HuaweiCloudNetworkProviderSpec extends Specification {

  @Subject
  HuaweiCloudNetworkProvider provider

  WriteableCache cache = new InMemoryCache()
  ObjectMapper mapper = new ObjectMapper()

  def setup() {
    provider = new HuaweiCloudNetworkProvider(cache, mapper)
    cache.mergeAll(Keys.Namespace.NETWORKS.ns, getAllNetworks())
  }

  void "getAll lists all and does not choke on deserializing routingConfig"() {
    when:
      def result = provider.getAll()

    then:
      result.size() == 2
  }

  @Shared
  List<Vpc> networkList = [
    Vpc.builder()
      .id('16c10a5d-572a-47bf-bf52-be3aacf15845')
      .name('some-network')
      .build(),

    Vpc.builder()
      .id('3b5ceb06-3b8d-43ee-866a-dc0443b85deg')
      .name('some-network-2')
      .build()
  ]

  private List<CacheData> getAllNetworks() {
    networkList.collect { Vpc vpc ->
      String cacheId = Keys.getNetworkKey(vpc.id, 'global', 'cn-north-1')
      Map<String, Object> attributes = [
        'id': vpc.id,
        'name': vpc.name
      ]
      return new DefaultCacheData(cacheId, attributes, [:])
    }
  }
}
