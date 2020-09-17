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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

public class EcsControllersSpec extends EcsSpec {

  @Autowired private ProviderRegistry providerRegistry;

  @DisplayName(".\n===\n" + "Given cached ECS cluster, retrieve it from /ecs/ecsClusters" + "\n===")
  @Test
  public void getEcsClustersTest() {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testClusterName = "integ-test-cluster";
    String testNamespace = Keys.Namespace.ECS_CLUSTERS.ns;

    String clusterKey = Keys.getClusterKey(ECS_ACCOUNT_NAME, TEST_REGION, testClusterName);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ECS_ACCOUNT_NAME);
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
    assertTrue(responseStr.contains(ECS_ACCOUNT_NAME));
    assertTrue(responseStr.contains(TEST_REGION));
  }

  @DisplayName(".\n===\n" + "Given cached ECS secret, retrieve it from /ecs/secrets" + "\n===")
  @Test
  public void getEcsSecretsTest() {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testSecretName = "tut/secret";
    String testNamespace = Keys.Namespace.SECRETS.ns;
    String testSecretArn = "arn:aws:secretsmanager:region:aws_account_id:secret:tut/sevret-jiObOV";

    String secretKey = Keys.getClusterKey(ECS_ACCOUNT_NAME, TEST_REGION, testSecretName);
    String url = getTestUrl("/ecs/secrets");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ECS_ACCOUNT_NAME);
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
    assertTrue(responseStr.contains(ECS_ACCOUNT_NAME));
    assertTrue(responseStr.contains(TEST_REGION));
    assertTrue(responseStr.contains(testSecretName));
    assertTrue(responseStr.contains(testSecretArn));
  }

  @DisplayName(
      ".\n===\n"
          + "Given cached service disc registry, retrieve it from /ecs/serviceDiscoveryRegistries"
          + "\n===")
  @Test
  public void getServiceDiscoveryRegistriesTest() {
    // given
    ProviderCache ecsCache = providerRegistry.getProviderCache(EcsProvider.NAME);
    String testRegistryId = "spinnaker-registry";
    String testNamespace = Keys.Namespace.SERVICE_DISCOVERY_REGISTRIES.ns;
    String testSdServiceArn =
        "arn:aws:servicediscovery:region:aws_account_id:service/srv-utcrh6wavdkggqtk";

    String serviceDiscoveryRegistryKey =
        Keys.getServiceDiscoveryRegistryKey(ECS_ACCOUNT_NAME, TEST_REGION, testRegistryId);
    String url = getTestUrl("/ecs/serviceDiscoveryRegistries");
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", ECS_ACCOUNT_NAME);
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
    assertTrue(responseStr.contains(ECS_ACCOUNT_NAME));
    assertTrue(responseStr.contains(TEST_REGION));
    assertTrue(responseStr.contains("spinnaker-demo"));
    assertTrue(responseStr.contains("srv-v001"));
    assertTrue(responseStr.contains(testSdServiceArn));
  }
}
