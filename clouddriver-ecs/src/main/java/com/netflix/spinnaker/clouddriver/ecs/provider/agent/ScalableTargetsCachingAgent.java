/*
 * Copyright 2017 Lookout, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SCALABLE_TARGETS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalableTargetsCachingAgent implements CachingAgent {
  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          Arrays.asList(AUTHORITATIVE.forType(SCALABLE_TARGETS.toString())));

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private NetflixAmazonCredentials account;
  private String accountName;
  private String region;

  public ScalableTargetsCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      ObjectMapper objectMapper) {
    this.region = region;
    this.account = account;
    this.accountName = account.getName();
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.objectMapper = objectMapper;
  }

  public static Map<String, Object> convertMetricAlarmToAttributes(
      ScalableTarget scalableTarget, ObjectMapper objectMapper) {
    return objectMapper.convertValue(scalableTarget, Map.class);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AWSApplicationAutoScaling autoScalingClient =
        amazonClientProvider.getAmazonApplicationAutoScaling(account, region, false);

    Set<ScalableTarget> scalableTargets = fetchScalableTargets(autoScalingClient);
    Map<String, Collection<CacheData>> newDataMap = generateFreshData(scalableTargets);
    Collection<CacheData> newData = newDataMap.get(SCALABLE_TARGETS.toString());

    Set<String> oldKeys =
        providerCache.getAll(SCALABLE_TARGETS.toString()).stream()
            .map(CacheData::getId)
            .filter(this::keyAccountRegionFilter)
            .collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = computeEvictableData(newData, oldKeys);

    return new DefaultCacheResult(newDataMap, evictionsByKey);
  }

  private Map<String, Collection<String>> computeEvictableData(
      Collection<CacheData> newData, Collection<String> oldKeys) {
    Set<String> newKeys = newData.stream().map(CacheData::getId).collect(Collectors.toSet());
    Set<String> evictedKeys =
        oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(SCALABLE_TARGETS.toString(), evictedKeys);
    log.info("Evicting " + evictedKeys.size() + " scalable targets in " + getAgentType());
    return evictionsByKey;
  }

  Map<String, Collection<CacheData>> generateFreshData(Set<ScalableTarget> scalableTargets) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();

    for (ScalableTarget scalableTarget : scalableTargets) {
      String key = Keys.getScalableTargetKey(accountName, region, scalableTarget.getResourceId());
      Map<String, Object> attributes = convertMetricAlarmToAttributes(scalableTarget, objectMapper);

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }

    log.info("Caching " + dataPoints.size() + " scalable targets in " + getAgentType());
    newDataMap.put(SCALABLE_TARGETS.toString(), dataPoints);
    return newDataMap;
  }

  Set<ScalableTarget> fetchScalableTargets(AWSApplicationAutoScaling autoScalingClient) {
    Set<ScalableTarget> scalableTargets = new HashSet<>();
    String nextToken = null;
    do {
      DescribeScalableTargetsRequest request =
          new DescribeScalableTargetsRequest().withServiceNamespace(ServiceNamespace.Ecs);
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      DescribeScalableTargetsResult result = autoScalingClient.describeScalableTargets(request);
      scalableTargets.addAll(result.getScalableTargets());

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return scalableTargets;
  }

  private boolean keyAccountRegionFilter(String key) {
    Map<String, String> keyParts = Keys.parse(key);
    return keyParts != null
        && keyParts.get("account").equals(accountName)
        && keyParts.get("region").equals(region);
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }
}
