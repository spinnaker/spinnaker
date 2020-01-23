/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TARGET_HEALTHS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.*;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsTargetGroupCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsTargetHealth;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsTargetGroup;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetHealthCachingAgent extends AbstractEcsAwsAwareCachingAgent<EcsTargetHealth>
    implements HealthProvidingCachingAgent {

  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(Arrays.asList(AUTHORITATIVE.forType(TARGET_HEALTHS.ns)));
  private static final String HEALTH_ID = "ecs-alb-target-health";

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;

  public TargetHealthCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      ObjectMapper objectMapper) {
    super(account, region, amazonClientProvider, awsCredentialsProvider);
    this.objectMapper = objectMapper;
  }

  @Override
  protected List<EcsTargetHealth> getItems(AmazonECS ecs, ProviderCache providerCache) {
    if (awsProviderCache == null) {
      throw new NullPointerException("awsProviderCache not initialized on " + getAgentType() + ".");
    }

    EcsTargetGroupCacheClient targetGroupCacheClient =
        new EcsTargetGroupCacheClient(awsProviderCache, objectMapper);
    Set<String> targetGroups = fetchTargetGroups(targetGroupCacheClient);

    AmazonElasticLoadBalancing amazonLoadBalancing =
        amazonClientProvider.getAmazonElasticLoadBalancingV2(account, region, false);

    List<EcsTargetHealth> targetHealthList = new LinkedList<>();

    if (targetGroups != null) {
      for (String tgArn : targetGroups) {

        DescribeTargetHealthResult describeTargetHealthResult = new DescribeTargetHealthResult();
        try {
          describeTargetHealthResult =
              amazonLoadBalancing.describeTargetHealth(
                  new DescribeTargetHealthRequest().withTargetGroupArn(tgArn));
        } catch (TargetGroupNotFoundException ignore) {
        }

        List<TargetHealthDescription> healthDescriptions =
            describeTargetHealthResult.getTargetHealthDescriptions();

        if (healthDescriptions != null && healthDescriptions.size() > 0) {
          targetHealthList.add(makeEcsTargetHealth(tgArn, healthDescriptions));
          log.debug(
              "Cached {} EcsTargetHealths for targetGroup {}", healthDescriptions.size(), tgArn);
        } else {
          log.debug("No TargetHealthDescriptions found for target group {}, skipping", tgArn);
        }
      }
    }

    return targetHealthList;
  }

  protected Set<String> fetchTargetGroups(EcsTargetGroupCacheClient cacheClient) {
    String searchKey =
        com.netflix.spinnaker.clouddriver.aws.data.Keys.getTargetGroupKey(
                "*", "*", region, "*", "*")
            + "*";

    Collection<String> targetGroupKeys =
        awsProviderCache.filterIdentifiers(TARGET_GROUPS.getNs(), searchKey);

    List<EcsTargetGroup> tgList = cacheClient.find(targetGroupKeys);

    return tgList.stream().map(EcsTargetGroup::getTargetGroupArn).collect(Collectors.toSet());
  }

  private EcsTargetHealth makeEcsTargetHealth(
      String targetGroupArn, List<TargetHealthDescription> healthDescriptions) {
    EcsTargetHealth targetHealth = new EcsTargetHealth();
    targetHealth.setTargetGroupArn(targetGroupArn);
    targetHealth.setTargetHealthDescriptions(healthDescriptions);

    return targetHealth;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(
      Collection<EcsTargetHealth> targetHealthList) {
    Collection<CacheData> dataPoints = new LinkedList<>();

    for (EcsTargetHealth targetHealth : targetHealthList) {
      Map<String, Object> attributes = convertToTargetHealthAttributes(targetHealth);
      String key = Keys.getTargetHealthKey(accountName, region, targetHealth.getTargetGroupArn());

      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " target health checks in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TARGET_HEALTHS.ns, dataPoints);

    return dataMap;
  }

  public static Map<String, Object> convertToTargetHealthAttributes(EcsTargetHealth targetHealth) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("targetGroupArn", targetHealth.getTargetGroupArn());
    attributes.put("targetHealthDescriptions", targetHealth.getTargetHealthDescriptions());
    return attributes;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public String getHealthId() {
    return HEALTH_ID;
  }
}
