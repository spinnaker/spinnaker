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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.INSTANCE_TYPES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.ecs.v1.domain.Flavor;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder.NamespaceCache;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

public class HuaweiCloudInstanceTypeCachingAgent extends AbstractHuaweiCloudCachingAgent {

  private static final Logger log =
      HuaweiCloudUtils.getLogger(HuaweiCloudInstanceTypeCachingAgent.class);

  public HuaweiCloudInstanceTypeCachingAgent(
      HuaweiCloudNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    super(credentials, objectMapper, region);
  }

  @Override
  String getAgentName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.unmodifiableCollection(
        new ArrayList<AgentDataType>() {
          {
            add(AUTHORITATIVE.forType(INSTANCE_TYPES.ns));
          }
        });
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    List<String> zones =
        credentials.getRegionToZones().getOrDefault(region, Collections.emptyList());
    if (zones.isEmpty()) {
      log.warn("no availability zones for region({})", region);
      return null;
    }

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(0);

    zones.forEach(
        zone -> {
          List<? extends Flavor> flavors = getCloudClient().getInstanceTypes(region, zone);
          if (!flavors.isEmpty()) {
            buildCacheData(cacheResultBuilder, flavors);
          }
        });

    return cacheResultBuilder.build();
  }

  private void buildCacheData(
      CacheResultBuilder cacheResultBuilder, List<? extends Flavor> flavors) {
    NamespaceCache nsCache = cacheResultBuilder.getNamespaceCache(INSTANCE_TYPES.ns);
    TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};

    flavors.forEach(
        flavor -> {
          nsCache
              .getCacheDataBuilder(
                  Keys.getInstanceTypeKey(flavor.getId(), getAccountName(), region))
              .setAttributes(objectMapper.convertValue(flavor, typeRef));
        });
  }
}
