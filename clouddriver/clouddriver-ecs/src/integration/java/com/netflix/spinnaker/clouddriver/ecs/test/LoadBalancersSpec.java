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

package com.netflix.spinnaker.clouddriver.ecs.test;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.amazonaws.services.ecs.model.LoadBalancer;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class LoadBalancersSpec extends EcsSpec {

  @Autowired private ProviderRegistry providerRegistry;

  @MockBean EcsAccountMapper mockEcsAccountMapper;

  @Test
  public void getLoadBalancersTest() {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testNamespace = Namespace.LOAD_BALANCERS.ns;
    String loadBalancerKey =
        com.netflix.spinnaker.clouddriver.aws.data.Keys.getLoadBalancerKey(
            testNamespace, "*", TEST_REGION, "*", "*");

    Set<String> keys = new HashSet<>();
    keys.add(
        "aws:targetGroups:my-aws-devel-acct:us-west-2:spinnaker-ecs-demo-artifacts-tg:ip:vpc-07daae48bf98a8fd8");

    List<String> securityGroups = new ArrayList<>();
    securityGroups.add("test-security");

    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put("targetGroups", keys);

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ECS_ACCOUNT_NAME);
    attributes.put("region", TEST_REGION);
    attributes.put("name", ECS_ACCOUNT_NAME);
    attributes.put("vpcId", "vpc-123");
    attributes.put("loadBalancerType", "test-type");
    attributes.put("securityGroups", securityGroups);
    attributes.put("targetGroups", "test-target");
    attributes.put("loadBalancerName", "testLB");

    DefaultCacheResult testResult =
        buildCacheResultForLB(attributes, testNamespace, loadBalancerKey, relationships);
    ecsCache.addCacheResult("TestAgent", Collections.singletonList(testNamespace), testResult);

    String url = getTestUrl("/ecs/loadBalancers");

    when(mockEcsAccountMapper.fromAwsAccountNameToEcsAccountName("*")).thenReturn(ECS_ACCOUNT_NAME);

    // when
    Response response =
        get(url).then().statusCode(200).contentType(ContentType.JSON).extract().response();

    String responseStr = response.asString();
    assertTrue(responseStr.contains(ECS_ACCOUNT_NAME));
    assertTrue(responseStr.contains(TEST_REGION));
    assertTrue(responseStr.contains("spinnaker-ecs-demo-artifacts-tg"));
    assertTrue(responseStr.contains("testLB"));
    assertTrue(responseStr.contains("vpc-123"));
    assertTrue(responseStr.contains("test-security"));
  }

  @Test
  public void getLoadBalancersForApplicationTest() {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testNamespaceForLB = Namespace.LOAD_BALANCERS.ns;
    String loadBalancerKey =
        com.netflix.spinnaker.clouddriver.aws.data.Keys.getLoadBalancerKey(
            testNamespaceForLB, "*", TEST_REGION, "*", "*");
    String targetGroup =
        "aws:targetGroups:aws-account:us-west-2:spinnaker-ecs-demo-tg:ip:vpc-07daae48bf98a8fd8";

    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put("loadBalancers", Arrays.asList(loadBalancerKey));
    relationships.put("targetGroups", Arrays.asList(targetGroup));

    Map<String, Object> LBAttributes = new HashMap<>();
    LBAttributes.put("account", ECS_ACCOUNT_NAME);
    LBAttributes.put("region", TEST_REGION);
    LBAttributes.put("name", ECS_ACCOUNT_NAME);
    LBAttributes.put("vpcId", "vpc-123");
    LBAttributes.put("loadBalancerType", "test-type");
    LBAttributes.put("securityGroups", Arrays.asList("test-security"));
    LBAttributes.put("targetGroups", "test-target");
    LBAttributes.put("loadBalancerName", "testLB");

    DefaultCacheResult testResult =
        buildCacheResultForLB(LBAttributes, testNamespaceForLB, loadBalancerKey, relationships);
    ecsCache.addCacheResult(
        "TestAgentLB", Collections.singletonList(testNamespaceForLB), testResult);

    LoadBalancer loadBalancer =
        new LoadBalancer()
            .withLoadBalancerName("testLB")
            .withTargetGroupArn(
                "arn:aws:elasticloadbalancing:us-west-2:910995322324:targetgroup/spinnaker-ecs-demo-tg/84e8edbbc69cd97b");

    Long createdAtLong = (new Date().getTime());

    String testNamespaceForService = SERVICES.ns;
    String serviceKey =
        com.netflix.spinnaker.clouddriver.ecs.cache.Keys.getServiceKey(
            ECS_ACCOUNT_NAME, "us-west-2", "TestAgentService");

    Map<String, Object> serviceAttributes = new HashMap<>();
    serviceAttributes.put("account", ECS_ACCOUNT_NAME);
    serviceAttributes.put("region", TEST_REGION);
    serviceAttributes.put("applicationName", "TestAgentService");
    serviceAttributes.put("loadBalancers", Arrays.asList(loadBalancer));
    serviceAttributes.put("serviceName", "testService");
    serviceAttributes.put("serviceArn", "service/testServiceArn");
    serviceAttributes.put("clusterName", "ecsTestCluster");
    serviceAttributes.put("clusterArn", "cluster/testClusterArn");
    serviceAttributes.put("roleArn", "role/testRoleArn");
    serviceAttributes.put("taskDefinition", "testTaskDefinition");
    serviceAttributes.put("desiredCount", 1);
    serviceAttributes.put("maximumPercent", 10);
    serviceAttributes.put("minimumHealthyPercent", 10);
    serviceAttributes.put("subnets", Arrays.asList("testSubnet"));
    serviceAttributes.put("securityGroups", Arrays.asList("test-security"));
    serviceAttributes.put("createdAt", createdAtLong);

    DefaultCacheResult testResultForService =
        buildCacheResult(serviceAttributes, testNamespaceForService, serviceKey);
    ecsCache.addCacheResult(
        "TestAgentService",
        Collections.singletonList(testNamespaceForService),
        testResultForService);

    String testNamespaceForTG = TARGET_GROUPS.ns;

    String targetGroupKey =
        com.netflix.spinnaker.clouddriver.aws.data.Keys.getTargetGroupKey(
            "spinnaker-ecs-demo-tg", "aws-account", TEST_REGION, "", "vpc-123");

    Map<String, Object> targetGroupAttributes = new HashMap<>();
    targetGroupAttributes.put("loadBalancerNames", Arrays.asList("ecsLB"));
    targetGroupAttributes.put("targetGroupName", "spinnaker-ecs-demo-tg");
    targetGroupAttributes.put("targetGroupArn", targetGroup);

    DefaultCacheResult testResultForTargetGroup =
        buildCacheResultForLB(
            targetGroupAttributes, testNamespaceForTG, targetGroupKey, relationships);
    ecsCache.addCacheResult(
        "TestAgentTG", Collections.singletonList(testNamespaceForTG), testResultForTargetGroup);

    when(mockEcsAccountMapper.fromAwsAccountNameToEcsAccountName("*")).thenReturn("ecs-account");
    when(mockEcsAccountMapper.fromEcsAccountNameToAwsAccountName(ECS_ACCOUNT_NAME))
        .thenReturn("aws-account");

    String url = getTestUrl("/applications/TestAgentService/loadBalancers");

    // when
    Response response = get(url).then().contentType(ContentType.JSON).extract().response();

    String responseStr = response.asString();
    assertTrue(responseStr.contains(ECS_ACCOUNT_NAME));
    assertTrue(responseStr.contains(TEST_REGION));
    assertTrue(responseStr.contains("spinnaker-ecs-demo-tg"));
    assertTrue(responseStr.contains("testLB"));
    assertTrue(responseStr.contains("vpc-123"));
    assertTrue(responseStr.contains("test-security"));
    assertTrue(responseStr.contains(targetGroup));
    assertTrue(
        responseStr.contains(
            "arn:aws:elasticloadbalancing:us-west-2:910995322324:targetgroup/spinnaker-ecs-demo-tg/84e8edbbc69cd97b"));
  }

  private DefaultCacheResult buildCacheResultForLB(
      Map<String, Object> attributes,
      String namespace,
      String key,
      Map<String, Collection<String>> relationships) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    dataPoints.add(new DefaultCacheData(key, attributes, relationships));

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(namespace, dataPoints);
    return new DefaultCacheResult(dataMap);
  }

  protected DefaultCacheResult buildCacheResult(
      Map<String, Object> attributes, String namespace, String key) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(namespace, dataPoints);

    return new DefaultCacheResult(dataMap);
  }
}
