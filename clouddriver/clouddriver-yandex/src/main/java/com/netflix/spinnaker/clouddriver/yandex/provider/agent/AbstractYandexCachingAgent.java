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

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.provider.YandexInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public abstract class AbstractYandexCachingAgent<T> implements CachingAgent, AccountAware {
  static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  private final String providerName = YandexInfrastructureProvider.class.getName();
  protected YandexCloudCredentials credentials;
  private ObjectMapper objectMapper;
  protected final YandexCloudFacade yandexCloudFacade;

  AbstractYandexCachingAgent(
      YandexCloudCredentials credentials,
      ObjectMapper objectMapper,
      YandexCloudFacade yandexCloudFacade) {
    this.credentials = credentials;
    this.objectMapper = objectMapper;
    this.yandexCloudFacade = yandexCloudFacade;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getClass().getSimpleName();
  }

  @Override
  public Set<AgentDataType> getProvidedDataTypes() {
    return Collections.singleton(AgentDataType.Authority.AUTHORITATIVE.forType(getType()));
  }

  String getFolder() {
    return credentials == null ? null : credentials.getFolder();
  }

  @Override
  public String getAccountName() {
    return credentials == null ? null : credentials.getName();
  }

  public Map<String, Object> convert(T object) {
    return getObjectMapper().convertValue(object, MAP_TYPE_REFERENCE);
  }

  protected Map<String, Collection<String>> getRelationships(T entity) {
    return Collections.emptyMap();
  }

  protected CacheData build(String key, T entity) {
    return new DefaultCacheData(key, convert(entity), getRelationships(entity));
  }

  protected <V> V convert(CacheData cacheData, Class<V> clazz) {
    return getObjectMapper().convertValue(cacheData.getAttributes(), clazz);
  }

  protected abstract List<T> loadEntities(ProviderCache providerCache);

  protected abstract String getKey(T entity);

  protected abstract String getType();

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    List<CacheData> cacheData =
        loadEntities(providerCache).stream()
            .map(entity -> build(getKey(entity), entity))
            .collect(Collectors.toList());
    return new DefaultCacheResult(Collections.singletonMap(getType(), cacheData));
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder, String key)
      throws IOException {
    Map<String, List<DefaultCacheData>> onDemandData =
        getObjectMapper()
            .readValue(
                (String)
                    cacheResultBuilder
                        .getOnDemand()
                        .getToKeep()
                        .get(key)
                        .getAttributes()
                        .get("cacheResults"),
                new TypeReference<Map<String, List<DefaultCacheData>>>() {});
    onDemandData.forEach(
        (namespace, cacheDatas) -> {
          if (namespace.equals(Keys.Namespace.ON_DEMAND.getNs())) {
            return;
          }

          cacheDatas.forEach(
              cacheData -> {
                CacheResultBuilder.CacheDataBuilder keep =
                    cacheResultBuilder.namespace(namespace).keep(cacheData.getId());
                keep.setAttributes(cacheData.getAttributes());
                keep.setRelationships(merge(keep.getRelationships(), cacheData.getRelationships()));
                cacheResultBuilder.getOnDemand().getToKeep().remove(cacheData.getId());
              });
        });
  }

  private static <K, V> Map<K, Collection<V>> merge(
      Map<K, Collection<V>> keep, Map<K, Collection<V>> onDemand) {
    Map<K, Collection<V>> result = new HashMap<>(keep);
    onDemand.forEach((k, v) -> result.merge(k, v, (o, n) -> o.addAll(n) ? o : n));
    return result;
  }
}
