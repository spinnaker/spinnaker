/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent;

import static com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys.Namespace.ON_DEMAND;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudUtils;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.huaweicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public abstract class AbstractOnDemandCachingAgent extends AbstractHuaweiCloudCachingAgent
    implements OnDemandAgent {

  private static final Logger log = HuaweiCloudUtils.getLogger(AbstractOnDemandCachingAgent.class);

  private final String namespace;

  public AbstractOnDemandCachingAgent(
      HuaweiCloudNamedAccountCredentials credentials,
      ObjectMapper objectMapper,
      String namespace,
      String region) {
    super(credentials, objectMapper, region);

    this.namespace = namespace;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<CacheData> datas = providerCache.getAll(ON_DEMAND.ns);
    if (HuaweiCloudUtils.isEmptyCollection(datas)) {
      return Collections.emptyList();
    }

    return datas.stream()
        .filter(
            cacheData -> {
              Map<String, String> parsedKey = Keys.parse(cacheData.getId());

              return !parsedKey.isEmpty()
                  && getAccountName().equals(parsedKey.get("account"))
                  && region.equals(parsedKey.get("region"));
            })
        .map(
            cacheData -> {
              Map<String, String> details = Keys.parse(cacheData.getId());
              Map<String, Object> attributes = cacheData.getAttributes();

              return new HashMap<String, Object>() {
                {
                  put("details", details);
                  put("moniker", convertOnDemandDetails(details));
                  put("cacheTime", attributes.get("cacheTime"));
                  put("processedTime", attributes.get("processedTime"));
                  put("processedCount", attributes.get("processedCount"));
                }
              };
            })
        .collect(Collectors.toList());
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long startTime = System.currentTimeMillis();
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime);

    buildCurrentNamespaceCacheData(cacheResultBuilder);

    Collection<String> keys =
        cacheResultBuilder.getNamespaceCache(this.namespace).getToKeep().keySet();

    Collection<CacheData> datas = providerCache.getAll(ON_DEMAND.ns, keys);
    if (!HuaweiCloudUtils.isEmptyCollection(datas)) {
      datas.forEach(
          cacheData -> {
            long cacheTime = 0; // The cache time of old cache data should be smaller than now.
            if (cacheData.getAttributes().get("cacheTime") != null) {
              cacheTime = (long) cacheData.getAttributes().get("cacheTime");
            }

            if (cacheTime < startTime) {
              // The "processedCount" will be set at bellow.
              int processedCount = 0;
              if (cacheData.getAttributes().get("processedCount") != null) {
                processedCount = (int) cacheData.getAttributes().get("processedCount");
              }

              if (processedCount > 0) {
                cacheResultBuilder.getOnDemand().getToEvict().add(cacheData.getId());
              } else {
                cacheResultBuilder.getOnDemand().getToKeep().put(cacheData.getId(), cacheData);
              }
            } else {
              // If the cache time is bigger than now, it shoud move the OnDemand cache data to
              // namespace cache. But how did it happen?
              log.warn(
                  "The cache time({}) of OnDemand data(key={}) is bigger than now({})",
                  cacheTime,
                  cacheData.getId(),
                  startTime);
            }
          });
    }

    CacheResult result = cacheResultBuilder.build();

    result
        .getCacheResults()
        .getOrDefault(ON_DEMAND.ns, Collections.emptyList())
        .forEach(
            cacheData -> {
              cacheData.getAttributes().put("processedTime", System.currentTimeMillis());

              int count = 0;
              if (cacheData.getAttributes().get("processedCount") != null) {
                count = (int) cacheData.getAttributes().get("processedCount");
              }
              cacheData.getAttributes().put("processedCount", count + 1);
            });

    return result;
  }

  protected OnDemandResult handle(ProviderCache providerCache, String name) {

    Optional<Object> resource = getMetricsSupport().readData(() -> getResourceByName(name));

    if (resource.isPresent()) {
      CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(Long.MAX_VALUE);

      buildSingleResourceCacheData(cacheResultBuilder, resource.get());

      CacheResult cacheResult = getMetricsSupport().transformData(() -> cacheResultBuilder.build());

      CacheData cacheData =
          getMetricsSupport()
              .onDemandStore(
                  () -> {
                    String cacheResults = "";
                    try {
                      cacheResults = objectMapper.writeValueAsString(cacheResult.getCacheResults());
                    } catch (Exception e) {
                      log.error("Error serializing cache results to string, error={}", e);
                    }
                    Map<String, Object> attributes = new HashMap();
                    attributes.put("cacheTime", System.currentTimeMillis());
                    attributes.put("cacheResults", cacheResults);
                    attributes.put("processedCount", 0);

                    return new DefaultCacheData(
                        getResourceCacheDataId(resource.get()),
                        (int) Duration.ofMinutes(10).getSeconds(),
                        Collections.unmodifiableMap(attributes),
                        Collections.emptyMap());
                  });
      providerCache.putCacheData(ON_DEMAND.ns, cacheData);

      return new OnDemandResult(this.getOnDemandAgentType(), cacheResult, Collections.emptyMap());
    }

    Collection<String> identifiers = getOnDemandKeysToEvict(providerCache, name);
    providerCache.evictDeletedItems(ON_DEMAND.ns, identifiers);

    return new OnDemandResult(
        this.getOnDemandAgentType(),
        new DefaultCacheResult(Collections.emptyMap()),
        new HashMap() {
          {
            put(namespace, identifiers);
          }
        });
  }

  /** Load the current resources in the cloud and build namespace cache data for them. */
  abstract void buildCurrentNamespaceCacheData(CacheResultBuilder cacheResultBuilder);

  /** Build namespace cache data of single resource. */
  abstract void buildSingleResourceCacheData(
      CacheResultBuilder cacheResultBuilder, Object resource);

  /** Load the single cloud resource(security group, server group etc) by name from cloud. */
  abstract Optional<Object> getResourceByName(String name);

  abstract String getResourceCacheDataId(Object resource);

  /** Build identifiers to evict when the resource of specified name is not found in cloud. */
  abstract Collection<String> getOnDemandKeysToEvict(ProviderCache providerCache, String name);
}
