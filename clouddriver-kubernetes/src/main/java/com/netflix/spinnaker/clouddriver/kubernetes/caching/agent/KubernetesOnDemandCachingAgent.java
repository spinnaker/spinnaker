/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.DefaultJsonCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KubernetesOnDemandCachingAgent extends KubernetesCachingAgent
    implements OnDemandAgent {
  private static final Logger log = LoggerFactory.getLogger(KubernetesOnDemandCachingAgent.class);
  @Getter protected final OnDemandMetricsSupport metricsSupport;

  protected static final String ON_DEMAND_TYPE = "onDemand";
  private static final String CACHE_TIME_KEY = "cacheTime";
  private static final String PROCESSED_COUNT_KEY = "processedCount";
  private static final String PROCESSED_TIME_KEY = "processedTime";
  private static final String CACHE_RESULTS_KEY = "cacheResults";
  private static final String MONIKER_KEY = "moniker";
  private static final String DETAILS_KEY = "details";

  protected KubernetesOnDemandCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount, agentInterval);

    metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, KubernetesCloudProvider.ID + ":" + OnDemandType.Manifest);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + ": agent is starting");
    Map<String, Object> details = defaultIntrospectionDetails();

    Long start = System.currentTimeMillis();
    Map<KubernetesKind, List<KubernetesManifest>> primaryResource = loadPrimaryResourceList();

    details.put("timeSpentInKubectlMs", System.currentTimeMillis() - start);

    List<String> primaryKeys =
        primaryResource.values().stream()
            .flatMap(Collection::stream)
            .map(mf -> Keys.InfrastructureCacheKey.createKey(mf, accountName))
            .collect(Collectors.toList());

    List<CacheData> keepInOnDemand = new ArrayList<>();
    List<CacheData> evictFromOnDemand = new ArrayList<>();

    Collection<String> existingKeys =
        providerCache.existingIdentifiers(ON_DEMAND_TYPE, primaryKeys);

    providerCache
        .getAll(ON_DEMAND_TYPE, existingKeys)
        .forEach(
            cd -> {
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
      if (!shouldOverwriteUsingOnDemand(start, onDemandData)) {
        continue;
      }

      String onDemandKey = onDemandData.getId();
      log.info(
          "{}: On demand entry '{}' is overwriting load data entry", getAgentType(), onDemandKey);

      String onDemandResultsJson = (String) onDemandData.getAttributes().get(CACHE_RESULTS_KEY);

      log.debug(
          "{}: On demand entry contents overwriting load data entry: {}",
          getAgentType(),
          onDemandResultsJson);
      Map<String, List<DefaultJsonCacheData>> onDemandResults;
      try {
        onDemandResults =
            objectMapper.readValue(
                onDemandResultsJson,
                new TypeReference<Map<String, List<DefaultJsonCacheData>>>() {});
      } catch (IOException e) {
        log.error("Failure parsing stored on demand data for '{}'", onDemandKey, e);
        continue;
      }

      mergeCacheResults(cacheResults, onDemandResults);
    }

    cacheResults.put(ON_DEMAND_TYPE, keepInOnDemand);
    Map<String, Collection<String>> evictionResults =
        new ImmutableMap.Builder<String, Collection<String>>()
            .put(
                ON_DEMAND_TYPE,
                evictFromOnDemand.stream().map(CacheData::getId).collect(Collectors.toList()))
            .build();

    return new DefaultCacheResult(cacheResults, evictionResults, details);
  }

  protected void mergeCacheResults(
      Map<String, Collection<CacheData>> current,
      Map<String, ? extends Collection<? extends CacheData>> added) {
    for (String group : added.keySet()) {
      Collection<CacheData> currentByGroup = current.get(group);
      Collection<? extends CacheData> addedByGroup = added.get(group);

      currentByGroup = currentByGroup == null ? new ArrayList<>() : currentByGroup;
      addedByGroup = addedByGroup == null ? new ArrayList<>() : addedByGroup;

      for (CacheData addedCacheData : addedByGroup) {
        CacheData mergedEntry =
            currentByGroup.stream()
                .filter(cd -> cd.getId().equals(addedCacheData.getId()))
                .findFirst()
                .flatMap(
                    cd ->
                        Optional.of(
                            KubernetesCacheDataConverter.mergeCacheData(cd, addedCacheData)))
                .orElse(addedCacheData);

        currentByGroup.removeIf(cd -> cd.getId().equals(addedCacheData.getId()));
        currentByGroup.add(mergedEntry);
      }

      current.put(group, currentByGroup);
    }
  }

  private boolean shouldOverwriteUsingOnDemand(Long startTime, CacheData onDemandEntry) {
    Map<String, Object> attributes = onDemandEntry.getAttributes();
    Long cacheTime = (Long) attributes.get(CACHE_TIME_KEY);

    return cacheTime != null && cacheTime >= startTime;
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

    return cacheTime >= lastFullRefresh || processedCount < 2;
  }

  private OnDemandAgent.OnDemandResult evictEntry(
      ProviderCache providerCache, KubernetesKind kind, String key) {
    Map<String, Collection<String>> evictions = new HashMap<>();
    CacheResult cacheResult = new DefaultCacheResult(new HashMap<>());

    log.info("{}: Evicting on demand '{}'", getAgentType(), key);
    providerCache.evictDeletedItems(ON_DEMAND_TYPE, ImmutableList.of(key));
    evictions.put(kind.toString(), ImmutableList.of(key));

    return new OnDemandAgent.OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  private OnDemandAgent.OnDemandResult addEntry(
      ProviderCache providerCache, String key, KubernetesManifest manifest)
      throws JsonProcessingException {
    Map<String, Collection<String>> evictions = new HashMap<>();
    CacheResult cacheResult;

    log.info("{}: Storing on demand '{}'", getAgentType(), key);
    cacheResult = buildCacheResult(manifest);
    String jsonResult = objectMapper.writeValueAsString(cacheResult.getCacheResults());
    log.debug("{}: On demand entry being written: {}", getAgentType(), jsonResult);

    Map<String, Object> attributes =
        new ImmutableMap.Builder<String, Object>()
            .put(CACHE_TIME_KEY, System.currentTimeMillis())
            .put(CACHE_RESULTS_KEY, jsonResult)
            .put(PROCESSED_COUNT_KEY, 0)
            .put(PROCESSED_TIME_KEY, -1)
            .put(MONIKER_KEY, credentials.getNamer().deriveMoniker(manifest))
            .build();

    Map<String, Collection<String>> relationships = new HashMap<>();
    CacheData onDemandData = new DefaultCacheData(key, attributes, relationships);
    providerCache.putCacheData(ON_DEMAND_TYPE, onDemandData);

    return new OnDemandAgent.OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  @Override
  public OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    String account = (String) data.get("account");
    String namespace = (String) data.get("location");
    String fullName = (String) data.get("name");

    if (Strings.isNullOrEmpty(account) || !getAccountName().equals(account)) {
      return null;
    }

    // No on-demand updates needed when live calls are used to check for status during orchestration
    if (credentials.isLiveManifestCalls()) {
      return null;
    }

    KubernetesCoordinates coords;
    try {
      coords =
          KubernetesCoordinates.builder().namespace(namespace).fullResourceName(fullName).build();
      if (!primaryKinds().contains(coords.getKind())) {
        return null;
      }

      if (coords.getName().isEmpty()) {
        return null;
      }
    } catch (IllegalArgumentException e) {
      // This is OK - the cache controller tries (w/o much info) to get every cache agent to handle
      // each request
      return null;
    }

    if (!coords.getNamespace().isEmpty()
        && !credentials.getKindProperties(coords.getKind()).isNamespaced()) {
      log.warn(
          "{}: Kind {} is not namespace but namespace {} was provided, ignoring",
          getAgentType(),
          coords.getKind(),
          coords.getNamespace());
      coords = coords.toBuilder().namespace("").build();
    }

    if (!handleNamespace(coords.getNamespace())) {
      return null;
    }

    log.info("{}: Accepted on demand refresh of '{}'", getAgentType(), data);
    OnDemandAgent.OnDemandResult result;
    KubernetesManifest manifest = loadPrimaryResource(coords);
    String resourceKey = Keys.InfrastructureCacheKey.createKey(account, coords);
    try {
      result =
          manifest == null
              ? evictEntry(providerCache, coords.getKind(), resourceKey)
              : addEntry(providerCache, resourceKey, manifest);
    } catch (Exception e) {
      log.error("Failed to process update of '{}'", resourceKey, e);
      return null;
    }

    log.info("{}: On demand cache refresh of (data: {}) succeeded", getAgentType(), data);
    return result;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.Manifest) && cloudProvider.equals(KubernetesCloudProvider.ID);
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    if (!handlePendingOnDemandRequests()) {
      return ImmutableList.of();
    }

    List<String> matchingKeys =
        providerCache.getIdentifiers(ON_DEMAND_TYPE).stream()
            .map(Keys::parseKey)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(k -> k instanceof Keys.InfrastructureCacheKey)
            .map(i -> (Keys.InfrastructureCacheKey) i)
            .filter(i -> i.getAccount().equals(getAccountName()))
            .map(Keys.InfrastructureCacheKey::toString)
            .collect(Collectors.toList());

    return providerCache.getAll(ON_DEMAND_TYPE, matchingKeys).stream()
        .map(
            cd -> {
              Keys.InfrastructureCacheKey parsedKey =
                  (Keys.InfrastructureCacheKey) Keys.parseKey(cd.getId()).get();
              Map<String, String> details = mapKeyToOnDemandResult(parsedKey);
              Map<String, Object> attributes = cd.getAttributes();
              return new ImmutableMap.Builder<String, Object>()
                  .put(DETAILS_KEY, details)
                  .put(MONIKER_KEY, attributes.get(MONIKER_KEY))
                  .put(CACHE_TIME_KEY, attributes.get(CACHE_TIME_KEY))
                  .put(PROCESSED_COUNT_KEY, attributes.get(PROCESSED_COUNT_KEY))
                  .put(PROCESSED_TIME_KEY, attributes.get(PROCESSED_TIME_KEY))
                  .build();
            })
        .collect(Collectors.toList());
  }

  private Map<String, String> mapKeyToOnDemandResult(Keys.InfrastructureCacheKey key) {
    return new ImmutableMap.Builder<String, String>()
        .put("name", KubernetesManifest.getFullResourceName(key.getKubernetesKind(), key.getName()))
        .put("account", key.getAccount())
        .put("location", key.getNamespace())
        .build();
  }

  /**
   * When fetching on-demand requests, we delegate to single caching agent per account to avoid
   * duplicate work. During caching cycles, when we make calls to kubernetes to fetch data about the
   * cluster and write this data to the cache, there is often a performance benefit to sharding work
   * among multiple agents so that it can happen in parallel.
   *
   * <p>When fetching on-demand requests, however, the current API is to return all on-demand
   * requests without any filtering. As the cache is not designed for a caching agent to be able to
   * query only its own on-demand requests, our options are: (1) Fan out to all the caching agents.
   * Each agent fetches all on-demand requests, then filters down to the ones that it owns. Then
   * combine these results to get the full set of on-demand requests. (2) Just pick a single caching
   * agent and have it get all the on-demand requests and return them.
   *
   * <p>For performance reasons, we'll go with option (2) and delegate a single caching agent to
   * return all on-demand cache requests for the account. As every account will have a
   * KubernetesCoreCachingAgent, we'll have that agent handle on-demand requests (by overriding this
   * function) while every other agent will ignore these.
   */
  protected boolean handlePendingOnDemandRequests() {
    return false;
  }

  private boolean handleNamespace(String namespace) {
    if (Strings.isNullOrEmpty(namespace)) {
      return handleClusterScopedResources();
    }
    return getNamespaces().contains(namespace);
  }
}
