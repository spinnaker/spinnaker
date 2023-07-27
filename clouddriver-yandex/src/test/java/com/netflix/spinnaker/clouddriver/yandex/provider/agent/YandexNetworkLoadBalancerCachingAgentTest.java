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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class YandexNetworkLoadBalancerCachingAgentTest {
  private static final String LOADBALANCER_NAME = "loadbalancer-test";
  private static final String ACCOUNT_NAME = "test-account";

  @Test
  void handleEviction() {
    YandexCloudCredentials cred = mock(YandexCloudCredentials.class);
    when(cred.getName()).thenReturn(ACCOUNT_NAME);
    YandexCloudFacade facade = mock(YandexCloudFacade.class);
    YandexCloudLoadBalancer balancer = new YandexCloudLoadBalancer();
    balancer.setAccount(ACCOUNT_NAME);
    balancer.setName(LOADBALANCER_NAME);
    when(facade.getLoadBalancer(any(), anyString())).thenReturn(balancer);
    cred.setName(ACCOUNT_NAME);
    YandexNetworkLoadBalancerCachingAgent agent =
        new YandexNetworkLoadBalancerCachingAgent(
            cred, new ObjectMapper(), new NoopRegistry(), facade);
    WriteableCache cache = new InMemoryCache();
    DefaultProviderCache providerCache = new DefaultProviderCache(cache);
    Map<String, Object> params = buildTestRequest(true);
    OnDemandAgent.OnDemandResult result = agent.handle(providerCache, params);
    assertNotNull(result);
    assertNull(result.getCacheResult());
    assertFalse(result.getEvictions().isEmpty());
  }

  @Test
  void handleReplacement() {
    YandexCloudCredentials cred = mock(YandexCloudCredentials.class);
    when(cred.getName()).thenReturn(ACCOUNT_NAME);
    YandexCloudFacade facade = mock(YandexCloudFacade.class);
    when(facade.getLoadBalancer(any(), anyString())).thenReturn(new YandexCloudLoadBalancer());
    cred.setName(ACCOUNT_NAME);
    YandexNetworkLoadBalancerCachingAgent agent =
        new YandexNetworkLoadBalancerCachingAgent(
            cred, new ObjectMapper(), new NoopRegistry(), facade);
    WriteableCache cache = new InMemoryCache();
    DefaultProviderCache providerCache = new DefaultProviderCache(cache);
    Map<String, Object> params = buildTestRequest(false);
    OnDemandAgent.OnDemandResult result = agent.handle(providerCache, params);
    assertNotNull(result);
    assertFalse(result.getCacheResult().getCacheResults().isEmpty());
    assertTrue(result.getCacheResult().getEvictions().isEmpty());
  }

  @NotNull
  private Map<String, Object> buildTestRequest(boolean evict) {
    Map<String, Object> params = new HashMap<>();
    params.put("loadBalancerName", "loadbalancer-test");
    params.put("region", "test-region");
    params.put("account", ACCOUNT_NAME);
    params.put("vpcId", "");
    params.put("evict", evict);
    return params;
  }
}
