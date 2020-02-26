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

import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.NETWORKS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.vpc.v1.domain.Vpc;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.model.HuaweiCloudNetwork;
import com.netflix.spinnaker.clouddriver.model.NetworkProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HuaweiCloudNetworkProvider implements NetworkProvider<HuaweiCloudNetwork> {
  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public HuaweiCloudNetworkProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getCloudProvider() {
    return HuaweiCloudProvider.ID;
  }

  @Override
  public Set<HuaweiCloudNetwork> getAll() {
    Collection<CacheData> data =
        cacheView.getAll(
            NETWORKS.ns,
            cacheView.filterIdentifiers(NETWORKS.ns, Keys.getNetworkKey("*", "*", "*")));

    if (HuaweiCloudUtils.isEmptyCollection(data)) {
      return Collections.emptySet();
    }

    return data.stream()
        .map(cacheData -> this.fromCacheData(cacheData))
        .filter(it -> it != null)
        .collect(Collectors.toSet());
  }

  private HuaweiCloudNetwork fromCacheData(CacheData cacheData) {
    Map<String, String> parts = Keys.parse(cacheData.getId(), NETWORKS);
    if (parts.isEmpty()) {
      return null;
    }

    Vpc vpc = objectMapper.convertValue(cacheData.getAttributes(), Vpc.class);

    return new HuaweiCloudNetwork(
        parts.get("id"), vpc.getName(), parts.get("region"), parts.get("account"));
  }
}
