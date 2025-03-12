/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunInstance;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunServerGroup;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudrunProviderUtils {
  public static CloudrunServerGroup serverGroupFromCacheData(
      ObjectMapper objectMapper, CacheData cacheData, Set<CloudrunInstance> instances) {
    CloudrunServerGroup serverGroup =
        objectMapper.convertValue(
            cacheData.getAttributes().get("serverGroup"), CloudrunServerGroup.class);
    serverGroup.setInstances(instances);
    return serverGroup;
  }

  public static CloudrunInstance instanceFromCacheData(
      ObjectMapper objectMapper, CacheData instanceData) {
    if (instanceData == null) {
      return null;
    } else {
      return objectMapper.convertValue(
          instanceData.getAttributes().get("instance"), CloudrunInstance.class);
    }
  }

  public static CloudrunLoadBalancer loadBalancerFromCacheData(
      ObjectMapper objectMapper,
      CacheData loadBalancerData,
      Set<CloudrunServerGroup> serverGroups) {
    CloudrunLoadBalancer loadBalancer =
        objectMapper.convertValue(
            loadBalancerData.getAttributes().get("loadBalancer"), CloudrunLoadBalancer.class);
    loadBalancer.setLoadBalancerServerGroups(serverGroups);
    return loadBalancer;
  }

  public static Collection<CacheData> resolveRelationshipData(
      Cache cacheView, CacheData source, String relationship) {

    // TODO - handle null source
    assert source != null;
    return cacheView.getAll(
        relationship,
        (source != null && source.getRelationships() != null)
            ? source.getRelationships().get(relationship)
            : new ArrayList<String>());
  }

  public static Collection<CacheData> resolveRelationshipDataForCollection(
      Cache cacheView,
      Collection<CacheData> sources,
      String relationship,
      CacheFilter cacheFilter) {
    List<String> relationships =
        sources.stream()
            .map(t -> t.getRelationships().get(relationship))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    return cacheView.getAll(relationship, relationships, cacheFilter);
  }

  public static Collection<CacheData> resolveRelationshipDataForCollection(
      Cache cacheView, Collection<CacheData> sources, String relationship) {
    return CloudrunProviderUtils.resolveRelationshipDataForCollection(
        cacheView, sources, relationship, null);
  }

  public static Map<String, Collection<CacheData>> preserveRelationshipDataForCollection(
      Cache cacheView,
      Collection<CacheData> sources,
      String relationship,
      CacheFilter cacheFilter) {
    Collection<CacheData> collection =
        resolveRelationshipDataForCollection(cacheView, sources, relationship, cacheFilter);
    Map<String, CacheData> allData = new HashMap<>();
    collection.forEach(v -> allData.put(v.getId(), v));
    Map<String, Collection<CacheData>> result = new HashMap<>();
    sources.forEach(
        source ->
            result.put(
                source.getId(),
                source.getRelationships().get(relationship).stream()
                    .map(t -> allData.get(t))
                    .collect(Collectors.toList())));
    return result;
  }

  public static Map<String, Collection<CacheData>> preserveRelationshipDataForCollection(
      Cache cacheView, Collection<CacheData> sources, String relationship) {
    return CloudrunProviderUtils.preserveRelationshipDataForCollection(
        cacheView, sources, relationship, null);
  }
}
