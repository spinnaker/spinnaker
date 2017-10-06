/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Namer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class KubernetesV2OnDemandCachingAgent<T> extends KubernetesV2CachingAgent<T> implements OnDemandAgent {
  @Getter
  protected final OnDemandMetricsSupport metricsSupport;

  protected final static String ON_DEMAND_TYPE = "onDemand";
  private final static String CACHE_TIME_KEY = "cacheTime";
  private final static String PROCESSED_COUNT_KEY = "processedCount";
  private final static String PROCESSED_TIME_KEY = "processedTime";
  private final static String CACHE_RESULTS_KEY = "cacheResults";
  private final static String MONIKER_KEY = "moniker";
  private final Namer<KubernetesManifest> namer;

  protected abstract List<T> loadPrimaryResourceList();
  protected abstract T loadPrimaryResource(String namespace, String name);
  protected abstract Class<T> primaryResourceClass();
  protected abstract OnDemandType onDemandType();
  protected abstract KubernetesKind primaryKind();
  protected abstract KubernetesApiVersion primaryApiVersion();

  /* Kind-of ugly... required for the on demand madness in orca that expects various fields like region, serverGroup, etc... */
  protected abstract Map<String, String> mapKeyToOnDemandResult(Keys.InfrastructureCacheKey key);
  protected abstract Optional<String> getResourceNameFromOnDemandRequest(Map<String, ?> request);

  protected KubernetesV2OnDemandCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount);
    namer = NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.getID())
        .withAccount(namedAccountCredentials.getName())
        .withResource(primaryResourceClass());

    metricsSupport = new OnDemandMetricsSupport(registry, this, KubernetesCloudProvider.getID() + ":" + onDemandType());
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    reloadNamespaces();

    Long start = System.currentTimeMillis();
    List<T> primaryResource = loadPrimaryResourceList();

    List<String> primaryKeys = primaryResource.stream()
        .map(rs -> objectMapper.convertValue(rs, KubernetesManifest.class))
        .map(mf -> Keys.infrastructure(mf, accountName))
        .collect(Collectors.toList());

    List<CacheData> keepInOnDemand = new ArrayList<>();
    List<CacheData> evictFromOnDemand = new ArrayList<>();

    providerCache.getAll(ON_DEMAND_TYPE, primaryKeys).forEach(cd -> {
      // can't be a ternary op due to restrictions on non-statement expressions in lambdas
      if (shouldKeepInOnDemand(start, cd)) {
        keepInOnDemand.add(cd);
      } else {
        evictFromOnDemand.add(cd);
      }
      processOnDemandEntry(cd);
    });

    // sort by increasing cache time to ensure newest entries are first
    keepInOnDemand.sort(Comparator.comparing(a -> ((Long) a.getAttributes().get(CACHE_TIME_KEY))));

    // first build the cache result, then decide which entries to overwrite with on demand data
    CacheResult result = buildCacheResult(primaryResource);
    Map<String, Collection<CacheData>> cacheResults = result.getCacheResults();

    for (CacheData onDemandData : keepInOnDemand) {
      String onDemandKey = onDemandData.getId();
      log.info("On demand entry '{}' is overwriting load data entry", onDemandKey);

      String onDemandResultsJson = (String) onDemandData.getAttributes().get(CACHE_RESULTS_KEY);
      Map<String, Collection<CacheData>> onDemandResults;
      try {
        onDemandResults = objectMapper.readValue(onDemandResultsJson, new TypeReference<Map<String, List<CacheData>>>() { });
      } catch (IOException e) {
        log.error("Failure parsing stored on demand data for '{}'", onDemandKey, e);
        continue;
      }

      mergeCacheResults(cacheResults, onDemandResults);
    }

    cacheResults.put(ON_DEMAND_TYPE, keepInOnDemand);
    Map<String, Collection<String>> evictionResults = new ImmutableMap.Builder<String, Collection<String>>()
        .put(ON_DEMAND_TYPE, evictFromOnDemand.stream().map(CacheData::getId).collect(Collectors.toList()))
        .build();

    return new DefaultCacheResult(cacheResults, evictionResults);
  }

  protected void mergeCacheResults(Map<String, Collection<CacheData>> current, Map<String, Collection<CacheData>> added) {
    for (String group : added.keySet()) {
      Collection<CacheData> currentByGroup = current.get(group);
      Collection<CacheData> addedByGroup = added.get(group);

      currentByGroup = currentByGroup == null ? new ArrayList<>() : currentByGroup;
      addedByGroup = addedByGroup == null ? new ArrayList<>() : addedByGroup;

      for (CacheData addedCacheData : addedByGroup) {
        CacheData mergedEntry = currentByGroup.stream()
            .filter(cd -> cd.getId().equals(addedCacheData.getId()))
            .findFirst()
            .flatMap(cd -> Optional.of(KubernetesCacheDataConverter.mergeCacheData(cd, addedCacheData)))
            .orElse(addedCacheData);

        currentByGroup.removeIf(cd -> cd.getId().equals(addedCacheData.getId()));
        currentByGroup.add(mergedEntry);
      }

      current.put(group, currentByGroup);
    }
  }

  private void processOnDemandEntry(CacheData onDemandEntry) {
    Map<String, Object> attributes = onDemandEntry.getAttributes();
    Integer processedCount = (Integer) attributes.get(PROCESSED_COUNT_KEY);
    Long processedTime = System.currentTimeMillis();

    processedCount = processedCount == null ? 0 : processedCount;
    processedCount += 1;

    attributes.put(PROCESSED_TIME_KEY, processedTime);
    attributes.put(PROCESSED_COUNT_KEY, processedCount);
  }

  private boolean shouldKeepInOnDemand(Long lastFullRefresh, CacheData onDemandEntry) {
    Map<String, Object> attributes = onDemandEntry.getAttributes();
    Long cacheTime = (Long) attributes.get(CACHE_TIME_KEY);
    Integer processedCount = (Integer) attributes.get(PROCESSED_COUNT_KEY);

    cacheTime = cacheTime == null ? 0L : cacheTime;
    processedCount = processedCount == null ? 0 : processedCount;

    return cacheTime >= lastFullRefresh || processedCount == 0;
  }

  private OnDemandAgent.OnDemandResult evictEntry(ProviderCache providerCache, String key) {
    Map<String, Collection<String>> evictions = new HashMap<>();
    CacheResult cacheResult = new DefaultCacheResult(new HashMap<>());

    log.info("Evicting on demand '{}'", key);
    providerCache.evictDeletedItems(ON_DEMAND_TYPE, Collections.singletonList(key));
    evictions.put(primaryKind().toString(), Collections.singletonList(key));

    return new OnDemandAgent.OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  private OnDemandAgent.OnDemandResult addEntry(ProviderCache providerCache, String key, T resource) throws JsonProcessingException {
    Map<String, Collection<String>> evictions = new HashMap<>();
    CacheResult cacheResult;

    log.info("Storing on demand '{}'", key);
    cacheResult = buildCacheResult(resource);
    KubernetesManifest manifest = objectMapper.convertValue(resource, KubernetesManifest.class);
    String jsonResult = objectMapper.writeValueAsString(cacheResult.getCacheResults());

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put(CACHE_TIME_KEY, System.currentTimeMillis())
        .put(CACHE_RESULTS_KEY, jsonResult)
        .put(PROCESSED_COUNT_KEY, 0)
        .put(PROCESSED_TIME_KEY, null)
        .put(MONIKER_KEY, namer.deriveMoniker(manifest))
        .build();

    Map<String, Collection<String>> relationships = new HashMap<>();
    CacheData onDemandData = new DefaultCacheData(key, attributes, relationships);
    providerCache.putCacheData(ON_DEMAND_TYPE, onDemandData);

    return new OnDemandAgent.OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  @Override
  public OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    String account = (String) data.get("accountName");
    String name = getResourceNameFromOnDemandRequest(data).orElse("");
    String namespace = (String) data.get("namespace");
    if (StringUtils.isEmpty(namespace)) {
      namespace = (String) data.get("region"); // sigh, namespace == region in k8s <-> spinnaker
    }

    reloadNamespaces();
    if (StringUtils.isEmpty(account)
        || StringUtils.isEmpty(name)
        || StringUtils.isEmpty(namespace)
        || !namespaces.contains(namespace)) {
      return null;
    }

    OnDemandAgent.OnDemandResult result;
    T resource = loadPrimaryResource(namespace, name);
    String resourceKey = Keys.infrastructure(primaryApiVersion(), primaryKind(), account, namespace, name);
    try {
      result = resource == null ? evictEntry(providerCache, resourceKey) : addEntry(providerCache, resourceKey, resource);
    } catch (Exception e) {
      log.error("Failed to process update of '{}'", resourceKey, e);
      return null;
    }

    log.info("On demand cache refresh of (data: {}) succeeded", data);
    return result;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type == onDemandType() && cloudProvider.equals(KubernetesCloudProvider.getID());
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys = providerCache.getIdentifiers(ON_DEMAND_TYPE);
    List<Keys.InfrastructureCacheKey> infraKeys = keys.stream()
        .map(Keys::parseKey)
        .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
        .filter(k -> k instanceof Keys.InfrastructureCacheKey)
        .map(i -> (Keys.InfrastructureCacheKey) i)
        .collect(Collectors.toList());

    List<String> matchingKeys = infraKeys.stream()
        .filter(i -> i.getAccount().equals(getAccountName())
            && namespaces.contains(i.getNamespace())
            && i.getKubernetesKind().equals(primaryKind()))
        .map(Keys.InfrastructureCacheKey::toString)
        .collect(Collectors.toList());

    return providerCache.getAll(ON_DEMAND_TYPE, matchingKeys).stream()
        .map(cd -> {
          Keys.InfrastructureCacheKey parsedKey = (Keys.InfrastructureCacheKey) Keys.parseKey(cd.getId()).get();
          Map<String, String> details = mapKeyToOnDemandResult(parsedKey);
          Map<String, Object> attributes = cd.getAttributes();
          return new ImmutableMap.Builder<String, Object>()
              .put("details", details)
              .put(MONIKER_KEY, attributes.get(MONIKER_KEY))
              .put(CACHE_TIME_KEY, attributes.get(CACHE_TIME_KEY))
              .put(PROCESSED_COUNT_KEY, attributes.get(PROCESSED_COUNT_KEY))
              .put(PROCESSED_TIME_KEY, attributes.get(PROCESSED_TIME_KEY))
              .build();
        })
        .collect(Collectors.toList());
  }
}
