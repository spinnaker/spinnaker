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
import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.IMAGES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.ims.v2.domain.Image;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder.NamespaceCache;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HuaweiCloudImageCachingAgent extends AbstractHuaweiCloudCachingAgent {

  public HuaweiCloudImageCachingAgent(
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
            add(AUTHORITATIVE.forType(IMAGES.ns));
          }
        });
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    List<? extends Image> images = getCloudClient().getImages(region);

    return buildCacheResult(images);
  }

  private CacheResult buildCacheResult(List<? extends Image> images) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(0);
    NamespaceCache nsCache = cacheResultBuilder.getNamespaceCache(IMAGES.ns);

    TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};

    images.forEach(
        image -> {
          nsCache
              .getCacheDataBuilder(Keys.getImageKey(image.getId(), getAccountName(), region))
              .setAttributes(objectMapper.convertValue(image, typeRef));
        });

    return cacheResultBuilder.build();
  }
}
