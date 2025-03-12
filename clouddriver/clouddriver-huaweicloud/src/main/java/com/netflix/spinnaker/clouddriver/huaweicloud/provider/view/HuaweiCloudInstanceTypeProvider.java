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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.INSTANCE_TYPES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.ecs.v1.domain.Flavor;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.model.HuaweiCloudInstanceType;
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HuaweiCloudInstanceTypeProvider
    implements InstanceTypeProvider<HuaweiCloudInstanceType> {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public HuaweiCloudInstanceTypeProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<HuaweiCloudInstanceType> getAll() {
    Collection<CacheData> data =
        cacheView.getAll(
            INSTANCE_TYPES.ns,
            cacheView.filterIdentifiers(INSTANCE_TYPES.ns, Keys.getInstanceTypeKey("*", "*", "*")));

    if (HuaweiCloudUtils.isEmptyCollection(data)) {
      return Collections.emptySet();
    }

    return data.stream()
        .map(cacheData -> this.fromCacheData(cacheData))
        .filter(it -> it != null)
        .collect(Collectors.toSet());
  }

  private HuaweiCloudInstanceType fromCacheData(CacheData cacheData) {
    Map<String, String> parts = Keys.parse(cacheData.getId(), INSTANCE_TYPES);
    if (parts.isEmpty()) {
      return null;
    }

    Flavor flavor = objectMapper.convertValue(cacheData.getAttributes(), Flavor.class);

    return new HuaweiCloudInstanceType(flavor.getName(), parts.get("region"), parts.get("account"));
  }
}
