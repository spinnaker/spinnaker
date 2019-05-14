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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ALARMS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
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

public class EcsCloudMetricAlarmCachingAgent implements CachingAgent {
  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(Arrays.asList(AUTHORITATIVE.forType(ALARMS.toString())));

  private final Logger log = LoggerFactory.getLogger(getClass());
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private NetflixAmazonCredentials account;
  private String accountName;
  private String region;

  public EcsCloudMetricAlarmCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider) {
    this.region = region;
    this.account = account;
    this.accountName = account.getName();
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
  }

  public static Map<String, Object> convertMetricAlarmToAttributes(
      MetricAlarm metricAlarm, String accountName, String region) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("alarmArn", metricAlarm.getAlarmArn());
    attributes.put("alarmName", metricAlarm.getAlarmName());
    attributes.put("alarmActions", metricAlarm.getAlarmActions());
    attributes.put("okActions", metricAlarm.getOKActions());
    attributes.put("insufficientDataActions", metricAlarm.getInsufficientDataActions());
    attributes.put("accountName", accountName);
    attributes.put("region", region);
    return attributes;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonCloudWatch cloudWatch = amazonClientProvider.getAmazonCloudWatch(account, region, false);

    Set<MetricAlarm> cacheableMetricAlarm = fetchMetricAlarms(cloudWatch);
    Map<String, Collection<CacheData>> newDataMap = generateFreshData(cacheableMetricAlarm);
    Collection<CacheData> newData = newDataMap.get(ALARMS.toString());

    Set<String> oldKeys =
        providerCache.getAll(ALARMS.toString()).stream()
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
    evictionsByKey.put(ALARMS.toString(), evictedKeys);
    log.info("Evicting " + evictedKeys.size() + " cloud metrics alarms in " + getAgentType());
    return evictionsByKey;
  }

  Map<String, Collection<CacheData>> generateFreshData(Set<MetricAlarm> cacheableMetricAlarm) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();

    for (MetricAlarm metricAlarm : cacheableMetricAlarm) {
      String key = Keys.getAlarmKey(accountName, region, metricAlarm.getAlarmArn());
      Map<String, Object> attributes =
          convertMetricAlarmToAttributes(metricAlarm, accountName, region);

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }

    log.info("Caching " + dataPoints.size() + " cloud metrics alarms in " + getAgentType());
    newDataMap.put(ALARMS.toString(), dataPoints);
    return newDataMap;
  }

  Set<MetricAlarm> fetchMetricAlarms(AmazonCloudWatch cloudWatch) {
    Set<MetricAlarm> cacheableMetricAlarm = new HashSet<>();
    String nextToken = null;
    do {
      DescribeAlarmsRequest request = new DescribeAlarmsRequest();
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      DescribeAlarmsResult describeAlarmsResult = cloudWatch.describeAlarms(request);
      cacheableMetricAlarm.addAll(describeAlarmsResult.getMetricAlarms());

      nextToken = describeAlarmsResult.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return cacheableMetricAlarm;
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
