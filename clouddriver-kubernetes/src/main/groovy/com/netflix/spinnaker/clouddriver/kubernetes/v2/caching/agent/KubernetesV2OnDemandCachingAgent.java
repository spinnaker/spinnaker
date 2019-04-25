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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Namer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.Manifest;

@Slf4j
public abstract class KubernetesV2OnDemandCachingAgent extends KubernetesV2CachingAgent implements OnDemandAgent {
  @Getter
  protected final OnDemandMetricsSupport metricsSupport;

  protected final static String ON_DEMAND_TYPE = "onDemand";
  private final static String CACHE_TIME_KEY = "cacheTime";
  private final static String PROCESSED_COUNT_KEY = "processedCount";
  private final static String PROCESSED_TIME_KEY = "processedTime";
  private final static String CACHE_RESULTS_KEY = "cacheResults";
  private final static String MONIKER_KEY = "moniker";
  private final static String DETAILS_KEY = "details";
  private final Namer<KubernetesManifest> namer;

  protected KubernetesV2OnDemandCachingAgent(KubernetesNamedAccountCredentials<KubernetesV2Credentials> namedAccountCredentials,
      KubernetesResourcePropertyRegistry resourcePropertyRegistry,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, resourcePropertyRegistry, objectMapper, registry, agentIndex, agentCount, agentInterval);
    namer = NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.getID())
        .withAccount(namedAccountCredentials.getName())
        .withResource(KubernetesManifest.class);

    metricsSupport = new OnDemandMetricsSupport(registry, this, KubernetesCloudProvider.getID() + ":" + Manifest);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + ": agent is starting");
    reloadNamespaces();
    Map<String, Object> details = defaultIntrospectionDetails();

    Long start = System.currentTimeMillis();
    Map<KubernetesKind, List<KubernetesManifest>> primaryResource;
    try {
      primaryResource = loadPrimaryResourceList();
    } catch (KubectlJobExecutor.NoResourceTypeException e) {
      log.error(getAgentType() + ": resource for this caching agent is not supported for this cluster. This will cause problems, please remove it from caching using the `omitKinds` config parameter.");
      return new DefaultCacheResult(new HashMap<>());
    }

    details.put("timeSpentInKubectlMs", System.currentTimeMillis() - start);

    List<String> primaryKeys = primaryResource.values()
        .stream()
        .flatMap(Collection::stream)
        .map(rs -> objectMapper.convertValue(rs, KubernetesManifest.class))
        .map(mf -> Keys.infrastructure(mf, accountName))
        .collect(Collectors.toList());

    List<CacheData> keepInOnDemand = new ArrayList<>();
    List<CacheData> evictFromOnDemand = new ArrayList<>();

    Collection<String> existingKeys = providerCache.existingIdentifiers(ON_DEMAND_TYPE, primaryKeys);

    providerCache.getAll(ON_DEMAND_TYPE, existingKeys).forEach(cd -> {
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
      log.info("{}: On demand entry '{}' is overwriting load data entry", getAgentType(), onDemandKey);

      String onDemandResultsJson = (String) onDemandData.getAttributes().get(CACHE_RESULTS_KEY);

      log.debug("{}: On demand entry contents overwriting load data entry: {}", getAgentType(), onDemandResultsJson);
      Map<String, Collection<CacheData>> onDemandResults;
      try {
        onDemandResults = objectMapper.readValue(onDemandResultsJson, new TypeReference<Map<String, List<DefaultCacheData>>>() { });
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

    return new DefaultCacheResult(cacheResults, evictionResults, details);
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

  private OnDemandAgent.OnDemandResult evictEntry(ProviderCache providerCache, KubernetesKind kind, String key) {
    Map<String, Collection<String>> evictions = new HashMap<>();
    CacheResult cacheResult = new DefaultCacheResult(new HashMap<>());

    log.info("{}: Evicting on demand '{}'", getAgentType(), key);
    providerCache.evictDeletedItems(ON_DEMAND_TYPE, Collections.singletonList(key));
    evictions.put(kind.toString(), Collections.singletonList(key));

    return new OnDemandAgent.OnDemandResult(getOnDemandAgentType(), cacheResult, evictions);
  }

  private OnDemandAgent.OnDemandResult addEntry(ProviderCache providerCache, String key, KubernetesManifest manifest) throws JsonProcessingException {
    Map<String, Collection<String>> evictions = new HashMap<>();
    CacheResult cacheResult;

    log.info("{}: Storing on demand '{}'", getAgentType(), key);
    cacheResult = buildCacheResult(manifest);
    String jsonResult = objectMapper.writeValueAsString(cacheResult.getCacheResults());
    log.debug("{}: On demand entry being written: {}", getAgentType(), jsonResult);

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put(CACHE_TIME_KEY, System.currentTimeMillis())
        .put(CACHE_RESULTS_KEY, jsonResult)
        .put(PROCESSED_COUNT_KEY, 0)
        .put(PROCESSED_TIME_KEY, -1)
        .put(MONIKER_KEY, namer.deriveMoniker(manifest))
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
    String name;
    KubernetesKind kind;

    if (StringUtils.isEmpty(account) || !getAccountName().equals(account)) {
      return null;
    }
    
    // No on-demand updates needed when live calls are used to check for status during orchestration
    if (credentials.isLiveManifestCalls()) {
      return null;
    }

    try {
      Pair<KubernetesKind, String> parsedName = KubernetesManifest.fromFullResourceName(fullName);
      kind = parsedName.getLeft();
      if (!primaryKinds().contains(kind)) {
        return null;
      }

      name = parsedName.getRight();
      if (StringUtils.isEmpty(name)) {
        return null;
      }
    } catch (Exception e) {
      // This is OK - the cache controller tries (w/o much info) to get every cache agent to handle each request
      return null;
    }

    if (!kind.isNamespaced() && StringUtils.isNotEmpty(namespace)) {
      log.warn("{}: Kind {} is not namespace but namespace {} was provided, ignoring", getAgentType(), kind, namespace);
      namespace = "";
    }

    reloadNamespaces();
    if (!StringUtils.isEmpty(namespace) && !namespaces.contains(namespace)) {
      return null;
    }

    log.info("{}: Accepted on demand refresh of '{}'", getAgentType(), data);
    OnDemandAgent.OnDemandResult result;
    KubernetesManifest manifest = loadPrimaryResource(kind, namespace, name);
    String resourceKey = Keys.infrastructure(kind, account, namespace, name);
    try {
      result = manifest == null ? evictEntry(providerCache, kind, resourceKey) : addEntry(providerCache, resourceKey, manifest);
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
    return type == Manifest && cloudProvider.equals(KubernetesCloudProvider.getID());
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    if (!handleReadRequests()) {
      return Collections.emptyList();
    }

    List<String> matchingKeys = providerCache.getIdentifiers(ON_DEMAND_TYPE).stream()
        .map(Keys::parseKey)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(k -> k instanceof Keys.InfrastructureCacheKey)
        .map(i -> (Keys.InfrastructureCacheKey) i)
        .filter(i -> i.getAccount().equals(getAccountName()) && primaryKinds().contains(i.getKubernetesKind()))
        .map(Keys.InfrastructureCacheKey::toString)
        .collect(Collectors.toList());

    return providerCache.getAll(ON_DEMAND_TYPE, matchingKeys).stream()
        .map(cd -> {
          Keys.InfrastructureCacheKey parsedKey = (Keys.InfrastructureCacheKey) Keys.parseKey(cd.getId()).get();
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
        .put("name", KubernetesManifest.getFullResourceName(
            key.getKubernetesKind(),
            key.getName()
        ))
        .put("account", key.getAccount())
        .put("location", key.getNamespace())
        .build();
  }

  /**
   * When fetching on-demand requests, we delegate to single caching agent, as the read request from the cache will
   * return results for all namespaces anyway. This way we avoid having all agents perform the same query and filter
   * to their namespaces, only to then re-combine all the results in the end.
   */
  private boolean handleReadRequests() {
    return agentIndex == 0;
  }
}
