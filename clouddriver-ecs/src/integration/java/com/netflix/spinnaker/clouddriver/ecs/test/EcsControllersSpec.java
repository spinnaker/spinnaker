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

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

public class EcsControllersSpec extends EcsSpec {

  @Autowired private ProviderRegistry providerRegistry;
  private AmazonECS mockECS = mock(AmazonECS.class);

  @DisplayName(
      ".\n===\n"
          + "Given cached ECS clusters (names), retrieve detailed description "
          + "of the cluster from /ecs/ecsDescribeClusters/{account}/{region}"
          + "\n===")
  @Test
  public void getAllEcsClusterDetailsTest() throws JsonProcessingException {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testClusterName = "example-app-test-Cluster-NSnYsTXmCfV2";
    String testNamespace = Keys.Namespace.ECS_CLUSTERS.ns;

    String clusterKey = Keys.getClusterKey(ECS_ACCOUNT_NAME, TEST_REGION, testClusterName);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ECS_ACCOUNT_NAME);
    attributes.put("region", TEST_REGION);
    attributes.put("clusterArn", "arn:aws:ecs:::cluster/" + testClusterName);
    attributes.put("clusterName", testClusterName);

    DefaultCacheResult testResult = buildCacheResult(attributes, testNamespace, clusterKey);
    ecsCache.addCacheResult("TestAgent", Collections.singletonList(testNamespace), testResult);

    when(mockAwsProvider.getAmazonEcs(any(NetflixECSCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockECS);

    Cluster clusterDecription =
        new Cluster()
            .withClusterArn("arn:aws:ecs:::cluster/" + testClusterName)
            .withStatus("ACTIVE")
            .withCapacityProviders("FARGATE", "FARGATE_SPOT")
            .withClusterName(testClusterName);
    when(mockECS.describeClusters(any(DescribeClustersRequest.class)))
        .thenReturn(new DescribeClustersResult().withClusters(clusterDecription));

    // when
    String testUrl =
        getTestUrl("/ecs/ecsClusterDescriptions/" + ECS_ACCOUNT_NAME + "/" + TEST_REGION);

    Response response =
        get(testUrl).then().statusCode(200).contentType(ContentType.JSON).extract().response();

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Collection<Cluster> clusters =
        Arrays.asList(objectMapper.readValue(response.asString(), Cluster[].class));
    // then
    assertNotNull(clusters);
    Cluster clusterDescription =
        (clusters.stream().filter(cluster -> cluster.getClusterName().equals(testClusterName)))
            .findAny()
            .get();
    assertTrue(clusterDescription.getClusterArn().contains(testClusterName));
    assertEquals(2, clusterDescription.getCapacityProviders().size());
    assertEquals("ACTIVE", clusterDescription.getStatus());
    assertTrue(clusterDescription.getCapacityProviders().contains("FARGATE"));
    assertTrue(clusterDescription.getCapacityProviders().contains("FARGATE_SPOT"));
  }

  @DisplayName(".\n===\n" + "Given cached ECS cluster, retrieve it from /ecs/ecsClusters" + "\n===")
  @ParameterizedTest
  @ValueSource(strings = {ECS_ACCOUNT_NAME, ECS_MONIKER_ACCOUNT_NAME})
  public void getEcsClustersTest(String accountName) {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testClusterName = "integ-test-cluster";
    String testNamespace = Keys.Namespace.ECS_CLUSTERS.ns;

    String clusterKey = Keys.getClusterKey(accountName, TEST_REGION, testClusterName);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", accountName);
    attributes.put("region", TEST_REGION);
    attributes.put("clusterArn", "arn:aws:ecs:::cluster/" + testClusterName);
    attributes.put("clusterName", testClusterName);

    DefaultCacheResult testResult = buildCacheResult(attributes, testNamespace, clusterKey);
    ecsCache.addCacheResult("TestAgent", Collections.singletonList(testNamespace), testResult);

    // when
    String testUrl = getTestUrl("/ecs/ecsClusters");

    Response response =
        get(testUrl).then().statusCode(200).contentType(ContentType.JSON).extract().response();

    // then
    assertNotNull(response);
    // TODO: serialize into expected return type to validate API contract hasn't changed
    String responseStr = response.asString();
    assertTrue(responseStr.contains(testClusterName));
    assertTrue(responseStr.contains(accountName));
    assertTrue(responseStr.contains(TEST_REGION));
  }

  @DisplayName(".\n===\n" + "Given cached ECS secret, retrieve it from /ecs/secrets" + "\n===")
  @ParameterizedTest
  @ValueSource(strings = {ECS_ACCOUNT_NAME, ECS_MONIKER_ACCOUNT_NAME})
  public void getEcsSecretsTest(String accountName) {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testSecretName = "tut/secret";
    String testNamespace = Keys.Namespace.SECRETS.ns;
    String testSecretArn = "arn:aws:secretsmanager:region:aws_account_id:secret:tut/sevret-jiObOV";

    String secretKey = Keys.getClusterKey(accountName, TEST_REGION, testSecretName);
    String url = getTestUrl("/ecs/secrets");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", accountName);
    attributes.put("region", TEST_REGION);
    attributes.put("secretName", testSecretName);
    attributes.put("secretArn", testSecretArn);

    DefaultCacheResult testResult = buildCacheResult(attributes, testNamespace, secretKey);
    ecsCache.addCacheResult("TestAgent", Collections.singletonList(testNamespace), testResult);

    // when
    Response response = get(url).then().contentType(ContentType.JSON).extract().response();

    // then
    assertNotNull(response);

    String responseStr = response.asString();
    assertTrue(responseStr.contains(accountName));
    assertTrue(responseStr.contains(TEST_REGION));
    assertTrue(responseStr.contains(testSecretName));
    assertTrue(responseStr.contains(testSecretArn));
  }

  @DisplayName(
      ".\n===\n"
          + "Given cached service disc registry, retrieve it from /ecs/serviceDiscoveryRegistries"
          + "\n===")
  @ParameterizedTest
  @ValueSource(strings = {ECS_ACCOUNT_NAME, ECS_MONIKER_ACCOUNT_NAME})
  public void getServiceDiscoveryRegistriesTest(String accountName) {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testRegistryId = "spinnaker-registry";
    String testNamespace = Keys.Namespace.SERVICE_DISCOVERY_REGISTRIES.ns;
    String testSdServiceArn =
        "arn:aws:servicediscovery:region:aws_account_id:service/srv-utcrh6wavdkggqtk";

    String serviceDiscoveryRegistryKey =
        Keys.getServiceDiscoveryRegistryKey(accountName, TEST_REGION, testRegistryId);
    String url = getTestUrl("/ecs/serviceDiscoveryRegistries");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", accountName);
    attributes.put("region", TEST_REGION);
    attributes.put("serviceName", "spinnaker-demo");
    attributes.put("serviceId", "srv-v001");
    attributes.put("serviceArn", testSdServiceArn);

    DefaultCacheResult testResult =
        buildCacheResult(attributes, testNamespace, serviceDiscoveryRegistryKey);
    ecsCache.addCacheResult("TestAgent", Collections.singletonList(testNamespace), testResult);

    // when
    Response response = get(url).then().contentType(ContentType.JSON).extract().response();

    // then
    assertNotNull(response);

    String responseStr = response.asString();
    assertTrue(responseStr.contains(accountName));
    assertTrue(responseStr.contains(TEST_REGION));
    assertTrue(responseStr.contains("spinnaker-demo"));
    assertTrue(responseStr.contains("srv-v001"));
    assertTrue(responseStr.contains(testSdServiceArn));
  }
}
