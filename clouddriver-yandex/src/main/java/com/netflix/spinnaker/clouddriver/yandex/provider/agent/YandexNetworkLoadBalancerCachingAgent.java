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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.yandex.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class YandexNetworkLoadBalancerCachingAgent
    extends AbstractYandexCachingAgent<YandexCloudLoadBalancer> implements OnDemandAgent {
  private static final String TYPE = Keys.Namespace.LOAD_BALANCERS.getNs();
  private static final String ON_DEMAND_NS = Keys.Namespace.ON_DEMAND.getNs();

  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private final OnDemandMetricsSupport metricsSupport;

  public YandexNetworkLoadBalancerCachingAgent(
      YandexCloudCredentials credentials,
      ObjectMapper objectMapper,
      Registry registry,
      YandexCloudFacade yandexCloudFacade) {
    super(credentials, objectMapper, yandexCloudFacade);
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, YandexCloudProvider.ID + ":" + OnDemandType.LoadBalancer);
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.LoadBalancer) && cloudProvider.equals(YandexCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    RefreshRequest request = getObjectMapper().convertValue(data, RefreshRequest.class);
    if (request.getLoadBalancerName() == null || !getAccountName().equals(request.getAccount())) {
      return null;
    }

    OnDemandResult result = new OnDemandResult();
    if (Boolean.TRUE.equals(request.getEvict())) {
      String pattern =
          Keys.getLoadBalancerKey(getAccountName(), "*", "*", request.getLoadBalancerName());
      Collection<String> keys = providerCache.filterIdentifiers(TYPE, pattern);
      result.setEvictions(Collections.singletonMap(TYPE, keys));
    } else {
      YandexCloudLoadBalancer loadBalancer =
          metricsSupport.readData(
              () -> yandexCloudFacade.getLoadBalancer(credentials, request.getLoadBalancerName()));

      CacheResult cacheResult =
          metricsSupport.transformData(
              () -> {
                CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();
                cacheResultBuilder.setStartTime(Long.MAX_VALUE);
                return buildCacheResult(
                    cacheResultBuilder, Collections.singletonList(loadBalancer));
              });

      metricsSupport.onDemandStore(
          () -> {
            CacheStats stats =
                new CacheStats(System.currentTimeMillis(), asString(cacheResult), 0, null);
            Map<String, Object> attributes =
                getObjectMapper().convertValue(stats, MAP_TYPE_REFERENCE);
            DefaultCacheData cacheData =
                new DefaultCacheData(
                    getKey(loadBalancer),
                    (int) TimeUnit.MINUTES.toSeconds(10),
                    attributes,
                    Collections.emptyMap());
            providerCache.putCacheData(ON_DEMAND_NS, cacheData);
            return null;
          });
      result.setCacheResult(cacheResult);
    }
    return result;
  }

  private String asString(CacheResult result) {
    try {
      return getObjectMapper().writeValueAsString(result.getCacheResults());
    } catch (JsonProcessingException ignored) {
      return null;
    }
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    List<String> ownedKeys =
        providerCache.getIdentifiers(ON_DEMAND_NS).stream()
            .filter(this::keyOwnedByThisAgent)
            .collect(Collectors.toList());

    return providerCache.getAll(ON_DEMAND_NS, ownedKeys).stream()
        .map(
            cacheData -> {
              Map<String, String> details = Keys.parse(cacheData.getId());
              CacheStats stats = convertToCacheStats(cacheData);
              Map<String, Object> map = new HashMap<>();
              map.put("details", details);
              map.put("moniker", convertOnDemandDetails(details));
              map.put("cacheTime", stats.getCacheTime());
              map.put("processedCount", stats.getProcessedCount());
              map.put("processedTime", stats.getProcessedTime());
              return map;
            })
        .collect(Collectors.toList());
  }

  private boolean keyOwnedByThisAgent(String key) {
    Map<String, String> parsedKey = Keys.parse(key);
    return parsedKey != null && parsedKey.get("type").equals(TYPE);
  }

  @Override
  protected List<YandexCloudLoadBalancer> loadEntities(ProviderCache providerCache) {
    return yandexCloudFacade.getLoadBalancers(credentials);
  }

  @Override
  protected String getKey(YandexCloudLoadBalancer loadBalancer) {
    return Keys.getLoadBalancerKey(
        getAccountName(), loadBalancer.getId(), getFolder(), loadBalancer.getName());
  }

  @Override
  protected String getType() {
    return TYPE;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    final CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();
    cacheResultBuilder.setStartTime(System.currentTimeMillis());

    List<YandexCloudLoadBalancer> loadBalancers = loadEntities(providerCache);
    List<String> loadBalancerKeys =
        loadBalancers.stream().map(this::getKey).collect(Collectors.toList());

    providerCache
        .getAll(ON_DEMAND_NS, loadBalancerKeys)
        .forEach(
            cacheData -> {
              // Ensure that we don't overwrite data that was inserted by the `handle` method while
              // we retrieved the
              // load balancers. Furthermore, cache data that hasn't been moved to the proper
              // namespace needs to be
              // updated in the ON_DEMAND cache, so don't evict data without a processedCount > 0.
              CacheResultBuilder.CacheMutation onDemand = cacheResultBuilder.getOnDemand();
              CacheStats stats = convertToCacheStats(cacheData);
              if (stats.cacheTime < cacheResultBuilder.getStartTime() && stats.processedCount > 0) {
                onDemand.getToEvict().add(cacheData.getId());
              } else {
                onDemand.getToKeep().put(cacheData.getId(), cacheData);
              }
            });

    CacheResult cacheResults = buildCacheResult(cacheResultBuilder, loadBalancers);
    if (cacheResults.getCacheResults() != null) {
      cacheResults
          .getCacheResults()
          .getOrDefault(ON_DEMAND_NS, Collections.emptyList())
          .forEach(
              cacheData -> {
                cacheData.getAttributes().put("processedTime", System.currentTimeMillis());
                cacheData
                    .getAttributes()
                    .compute(
                        "processedCount", (key, count) -> (count != null ? (Long) count : 0) + 1);
              });
    }
    return cacheResults;
  }

  private CacheResult buildCacheResult(
      final CacheResultBuilder cacheResultBuilder, List<YandexCloudLoadBalancer> loadBalancers) {
    loadBalancers.forEach(
        loadBalancer -> {
          String loadBalancerKey = getKey(loadBalancer);
          if (shouldUseOnDemandData(cacheResultBuilder, loadBalancerKey)) {
            try {
              moveOnDemandDataToNamespace(cacheResultBuilder, loadBalancerKey);
            } catch (IOException e) {
              // CatsOnDemandCacheUpdater handles this
              throw new UncheckedIOException(e);
            }
          } else {
            CacheResultBuilder.CacheDataBuilder keep =
                cacheResultBuilder.namespace(TYPE).keep(loadBalancerKey);
            keep.setAttributes(convert(loadBalancer));
          }
        });
    return cacheResultBuilder.build();
  }

  private boolean shouldUseOnDemandData(
      CacheResultBuilder cacheResultBuilder, String loadBalancerKey) {
    Optional<CacheStats> stats =
        Optional.ofNullable(cacheResultBuilder.getOnDemand().getToKeep().get(loadBalancerKey))
            .map(this::convertToCacheStats);
    return stats.isPresent() && stats.get().getCacheTime() >= cacheResultBuilder.getStartTime();
  }

  private CacheStats convertToCacheStats(CacheData cacheData) {
    return convert(cacheData, CacheStats.class);
  }

  @AllArgsConstructor
  @NoArgsConstructor
  @Data
  static class CacheStats {
    Long cacheTime;
    String cacheResults;
    Integer processedCount;
    Long processedTime;
  }

  @Data
  static class RefreshRequest {
    private String account;
    private String loadBalancerName;
    private Boolean evict;
    private String vpcId;
    private String region;
  }
}
