/*
 * Copyright 2021 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.lambda.provider.agent;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import com.netflix.spinnaker.clouddriver.lambda.service.config.LambdaServiceConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LambdaCachingAgentTest {
  private ObjectMapper objectMapper = new ObjectMapper();
  private AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  private String REGION = "us-west-2";
  private NetflixAmazonCredentials netflixAmazonCredentials = mock(NetflixAmazonCredentials.class);
  private LambdaServiceConfig config = mock(LambdaServiceConfig.class);
  private ServiceLimitConfiguration serviceLimitConfiguration =
      mock(ServiceLimitConfiguration.class);
  private LambdaCachingAgent lambdaCachingAgent;
  private final ProviderCache cache = mock(ProviderCache.class);

  @BeforeEach
  public void setup() {
    when(config.getRetry()).thenReturn(new LambdaServiceConfig.Retry());
    when(serviceLimitConfiguration.getLimit(any(), any(), any(), any(), any())).thenReturn(1.0);
    lambdaCachingAgent =
        new LambdaCachingAgent(
            objectMapper,
            clientProvider,
            netflixAmazonCredentials,
            REGION,
            config,
            serviceLimitConfiguration);
  }

  @Test
  public void shouldGetAuthoritativeName() {
    assertThat(lambdaCachingAgent.getAuthoritativeKeyName()).isEqualTo("lambdaFunctions");
  }

  @Test
  public void shouldReturnEvictions() {
    when(netflixAmazonCredentials.getName()).thenReturn("test-account");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("functionName", "function-3");
    Collection<CacheData> data = new HashSet<>();
    data.add(
        new DefaultCacheData(
            Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-3"),
            attributes,
            Collections.emptyMap()));

    HashSet<String> oldKeys = new HashSet<>();
    oldKeys.add(
        Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-1"));
    oldKeys.add(
        Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-2"));

    when(cache.getIdentifiers(any())).thenReturn(oldKeys);

    Map<String, Collection<String>> evictions =
        lambdaCachingAgent.computeEvictableData(data, cache);

    assertThat(evictions.get(lambdaCachingAgent.getAuthoritativeKeyName()).size()).isEqualTo(2);
    assertThat(evictions.get(lambdaCachingAgent.getAuthoritativeKeyName())).isEqualTo(oldKeys);
  }

  @Test
  public void shouldNotEvictionNewData() {
    when(netflixAmazonCredentials.getName()).thenReturn("test-account");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("functionName", "function-1");
    Collection<CacheData> data = new HashSet<>();
    data.add(
        new DefaultCacheData(
            Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-1"),
            attributes,
            Collections.emptyMap()));

    Collection<String> oldKeys =
        List.of(
            Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-1"),
            Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-2"));

    when(cache.getIdentifiers(any())).thenReturn(oldKeys);

    Map<String, Collection<String>> evictions =
        lambdaCachingAgent.computeEvictableData(data, cache);

    assertThat(evictions.get(lambdaCachingAgent.getAuthoritativeKeyName()).size()).isEqualTo(1);
    assertThat(evictions.get(lambdaCachingAgent.getAuthoritativeKeyName()).stream().findAny().get())
        .isNotEqualTo(
            Keys.getLambdaFunctionKey(netflixAmazonCredentials.getName(), REGION, "function-1"));
  }

  @Test
  public void buildCacheDataShouldAddInfo() {
    ConcurrentHashMap<String, CacheData> lambdaCacheData = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Collection<String>> appLambdaRelationships =
        new ConcurrentHashMap<>();
    List<Map<String, Object>> allLambdas =
        List.of(
            Map.of("functionName", "appName-functionName-something"),
            Map.of("functionName", "appName2-functionName2-something2"));

    lambdaCachingAgent.buildCacheData(lambdaCacheData, appLambdaRelationships, allLambdas);

    assertThat(lambdaCacheData.size()).isEqualTo(2);
    assertThat(appLambdaRelationships.size()).isEqualTo(2);
  }
}
