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
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexApplication;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class YandexApplicationProvider implements ApplicationProvider {
  private final CacheClient<YandexApplication> cacheClient;

  @Autowired
  YandexApplicationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheClient =
        new CacheClient<>(
            cacheView, objectMapper, Keys.Namespace.APPLICATIONS, YandexApplication.class);
  }

  @Override
  public Set<YandexApplication> getApplications(boolean expand) {
    Set<YandexApplication> result = cacheClient.getAll(Keys.APPLICATION_WILDCARD);
    if (expand) {
      result.forEach(this::updateRelations);
    }
    return result;
  }

  @Override
  public YandexApplication getApplication(String name) {
    return cacheClient
        .findOne(Keys.getApplicationKey(name))
        .map(this::updateRelations)
        .orElse(null);
  }

  public Collection<String> getRelationship(String key, Keys.Namespace namespace) {
    return cacheClient.getRelationKeys(key, namespace);
  }

  private YandexApplication updateRelations(YandexApplication application) {
    String applicationKey = Keys.getApplicationKey(application.getName());
    application.getClusterNames().putAll(getClusters(applicationKey));
    application.getInstances().addAll(getInstances(applicationKey));
    return application;
  }

  @NotNull
  private List<Map<String, String>> getInstances(String key) {
    return cacheClient.getRelationKeys(key, Keys.Namespace.INSTANCES).stream()
        .map(Keys::parse)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @NotNull
  private Map<String, Set<String>> getClusters(String key) {
    return cacheClient.getRelationKeys(key, Keys.Namespace.CLUSTERS).stream()
        .map(Keys::parse)
        .filter(Objects::nonNull)
        .collect(
            Collectors.groupingBy(
                parts -> parts.get("account"),
                Collectors.mapping(parts -> parts.get("name"), Collectors.toSet())));
  }
}
