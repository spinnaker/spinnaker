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

import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.IMAGES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.openstack4j.openstack.ims.v2.domain.Image;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.controller.ImageProvider;
import com.netflix.spinnaker.clouddriver.huaweicloud.model.HuaweiCloudImage;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HuaweiCloudImageProvider implements ImageProvider {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public HuaweiCloudImageProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<HuaweiCloudImage> getAll(String account, String region) {
    Collection<CacheData> data =
        cacheView.getAll(
            IMAGES.ns,
            cacheView.filterIdentifiers(
                IMAGES.ns,
                Keys.getImageKey(
                    "*",
                    StringUtils.isEmpty(account) ? "*" : account,
                    StringUtils.isEmpty(region) ? "*" : region)));

    if (HuaweiCloudUtils.isEmptyCollection(data)) {
      return Collections.emptySet();
    }

    return data.stream()
        .map(cacheData -> this.fromCacheData(cacheData))
        .filter(it -> it != null)
        .collect(Collectors.toSet());
  }

  HuaweiCloudImage fromCacheData(CacheData cacheData) {
    Map<String, String> parts = Keys.parse(cacheData.getId(), IMAGES);
    if (parts.isEmpty()) {
      return null;
    }

    Image image = objectMapper.convertValue(cacheData.getAttributes(), Image.class);

    return new HuaweiCloudImage(
        image.getId(), image.getName(), parts.get("region"), parts.get("account"));
  }
}
