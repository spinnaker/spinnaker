/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.cache.Caches;
import io.kubernetes.client.informer.cache.DeltaFIFO;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A lightweight cache implementation that stores only keys in memory. This is used to track the
 * known keys of Kubernetes objects in the KubernetesInformer. Informers use this cache to
 * distinguish between objects that are already known and those that are new. Also, they track the
 * objects that should be deleted after a resync.
 *
 * @param <ApiType> is always a {@link DynamicKubernetesObject}.
 */
@Slf4j
public class KubernetesInformerCache<ApiType extends KubernetesObject> implements Indexer<ApiType> {

  private final ConcurrentHashMap<String, Boolean> knownKeys;

  public KubernetesInformerCache() {
    this.knownKeys = new ConcurrentHashMap<>();
  }

  public void loadFromPersistentCache(List<InfrastructureCacheKey> cachedKeys) {
    for (InfrastructureCacheKey cachedKey : cachedKeys) {
      String key = toKey(cachedKey.getNamespace(), cachedKey.getName());
      this.knownKeys.put(key, Boolean.TRUE);
    }
  }

  @Override
  public void add(ApiType obj) {
    String key = toKey(obj);
    this.knownKeys.put(key, Boolean.TRUE);
  }

  @Override
  public void update(ApiType obj) {
    String key = toKey(obj);
    this.knownKeys.put(key, Boolean.TRUE);
  }

  @Override
  public void delete(ApiType obj) {
    String key = toKey(obj);
    this.knownKeys.remove(key);
  }

  @Override
  public void resync() {}

  @Override
  public List<String> listKeys() {
    return List.copyOf(this.knownKeys.keySet());
  }

  @Override
  public ApiType get(ApiType obj) {
    String key = toKey(obj);
    if (!this.knownKeys.containsKey(key)) {
      return null;
    }
    return getEmptyObject(obj.getMetadata().getNamespace(), obj.getMetadata().getName());
  }

  @Override
  public ApiType getByKey(String key) {
    if (!this.knownKeys.containsKey(key)) {
      return null;
    }
    String[] split = StringUtils.split(key, "/", 2);
    if (ArrayUtils.isEmpty(split)) {
      log.warn("Invalid key format: {}", key);
      return null;
    }

    return split.length == 1 ? getEmptyObject(null, split[0]) : getEmptyObject(split[0], split[1]);
  }

  private ApiType getEmptyObject(String namespace, String name) {
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    if (StringUtils.isNotBlank(namespace)) {
      meta.setNamespace(namespace);
    }

    DynamicKubernetesObject obj = new DynamicKubernetesObject();
    obj.setMetadata(meta);

    return (ApiType) obj;
  }

  @Override
  public List<ApiType> list() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void replace(List<ApiType> list, String resourceVersion) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ApiType> index(String indexName, ApiType obj) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> indexKeys(String indexName, String indexKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ApiType> byIndex(String indexName, String indexKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, Function<ApiType, List<String>>> getIndexers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addIndexers(Map<String, Function<ApiType, List<String>>> indexers) {
    throw new UnsupportedOperationException();
  }

  private String toKey(ApiType obj) {
    if (obj instanceof DeltaFIFO.DeletedFinalStateUnknown) {
      return Caches.deletionHandlingMetaNamespaceKeyFunc(obj);
    }
    return toKey(obj.getMetadata().getNamespace(), obj.getMetadata().getName());
  }

  private String toKey(String namespace, String name) {
    if (StringUtils.isBlank(namespace)) {
      return name;
    }
    return namespace + "/" + name;
  }
}
