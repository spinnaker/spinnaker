/*
 * Copyright 2020 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.SUBNETS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.vpc.v1.domain.Subnet;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.model.HuaweiCloudSubnet;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class HuaweiCloudSubnetProvider implements SubnetProvider<HuaweiCloudSubnet> {
  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public HuaweiCloudSubnetProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getCloudProvider() {
    return HuaweiCloudProvider.ID;
  }

  @Override
  public Set<HuaweiCloudSubnet> getAll() {
    Collection<CacheData> data =
        cacheView.getAll(
            SUBNETS.ns, cacheView.filterIdentifiers(SUBNETS.ns, Keys.getSubnetKey("*", "*", "*")));

    if (HuaweiCloudUtils.isEmptyCollection(data)) {
      return Collections.emptySet();
    }

    return data.stream()
        .map(cacheData -> this.fromCacheData(cacheData))
        .filter(it -> it != null)
        .collect(Collectors.toSet());
  }

  private HuaweiCloudSubnet fromCacheData(CacheData cacheData) {
    Map<String, String> parts = Keys.parse(cacheData.getId(), SUBNETS);
    if (parts.isEmpty()) {
      return null;
    }

    Subnet subnet = objectMapper.convertValue(cacheData.getAttributes(), Subnet.class);

    return new HuaweiCloudSubnet(
        parts.get("id"),
        subnet.getName(),
        subnet.getCidr(),
        subnet.getVpcId(),
        parts.get("region"),
        parts.get("account"),
        "n/a");
  }
}
