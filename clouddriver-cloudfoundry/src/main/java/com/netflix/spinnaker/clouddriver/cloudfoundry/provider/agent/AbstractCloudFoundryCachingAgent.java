/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.Views;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import java.io.IOException;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
abstract class AbstractCloudFoundryCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware {
  private final String providerName = CloudFoundryProvider.class.getName();
  private static final ObjectMapper cacheViewMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final String account;
  private final OnDemandMetricsSupport metricsSupport;
  private final CloudFoundryClient client;
  private final Clock internalClock;

  AbstractCloudFoundryCachingAgent(String account, CloudFoundryClient client, Registry registry) {
    this(account, client, registry, Clock.systemDefaultZone());
  }

  private AbstractCloudFoundryCachingAgent(
      String account, CloudFoundryClient client, Registry registry, Clock internalClock) {
    this.account = account;
    this.client = client;
    cacheViewMapper.setConfig(cacheViewMapper.getSerializationConfig().withView(Views.Cache.class));
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, CloudFoundryProvider.PROVIDER_ID + ":" + OnDemandType.ServerGroup);
    this.internalClock = internalClock;
  }

  @Override
  public String getAccountName() {
    return account;
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getClass().getSimpleName();
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  /**
   * Serialize just enough data to be able to reconstitute the model fully if its relationships are
   * also deserialized.
   */
  // Visible for testing
  static Map<String, Object> cacheView(Object o) {
    return Collections.singletonMap(
        "resource", cacheViewMapper.convertValue(o, new TypeReference<Map<String, Object>>() {}));
  }

  Map<String, Collection<ResourceCacheData>> getCacheResultsFromCacheData(CacheData cacheData) {
    try {
      return cacheViewMapper.readValue(
          cacheData.getAttributes().get("cacheResults").toString(),
          new TypeReference<Map<String, Collection<ResourceCacheData>>>() {});
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize cache results", e);
    }
  }

  void processOnDemandCacheData(CacheData cacheData) {
    Map<String, Object> attributes = cacheData.getAttributes();
    attributes.put("processedTime", System.currentTimeMillis());
    attributes.put("processedCount", (Integer) attributes.getOrDefault("processedCount", 0) + 1);
  }

  CacheData buildOnDemandCacheData(String key, Map<String, Collection<CacheData>> cacheResult) {
    try {
      return new DefaultCacheData(
          key,
          (int) TimeUnit.MINUTES.toSeconds(10), // ttl
          io.vavr.collection.HashMap.<String, Object>of(
                  "cacheTime",
                  this.getInternalClock().instant().toEpochMilli(),
                  "cacheResults",
                  cacheViewMapper.writeValueAsString(cacheResult),
                  "processedCount",
                  0)
              .toJavaMap(),
          emptyMap(),
          this.getInternalClock());
    } catch (JsonProcessingException serializationException) {
      throw new RuntimeException("cache results serialization failed", serializationException);
    }
  }
}
