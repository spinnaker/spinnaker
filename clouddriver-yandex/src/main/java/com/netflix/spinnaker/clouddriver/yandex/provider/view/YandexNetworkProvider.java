/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.model.NetworkProvider;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudNetwork;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YandexNetworkProvider implements NetworkProvider<YandexCloudNetwork> {
  private final CacheClient<YandexCloudNetwork> cacheClient;

  @Override
  public String getCloudProvider() {
    return YandexCloudProvider.ID;
  }

  @Autowired
  public YandexNetworkProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheClient =
        new CacheClient<>(
            cacheView, objectMapper, Keys.Namespace.NETWORKS, YandexCloudNetwork.class);
  }

  @Override
  public Set<YandexCloudNetwork> getAll() {
    return cacheClient.getAll(Keys.NETWORK_WILDCARD);
  }
}
