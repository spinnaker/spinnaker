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

package com.netflix.spinnaker.clouddriver.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(EcsTestConfiguration.class)
@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
public class EcsSpec {
  protected static final String TEST_OPERATIONS_LOCATION =
      "src/integration/resources/testoperations";
  protected static final String TEST_ARTIFACTS_LOCATION = "src/integration/resources/testartifacts";

  @Value("${ecs.primaryAccount}")
  protected String ECS_ACCOUNT_NAME;

  protected final String TEST_REGION = "us-west-2";
  protected final int TASK_RETRY_SECONDS = 3;
  protected static final String CREATE_SG_TEST_PATH = "/ecs/ops/createServerGroup";

  @Value("${ecs.enabled}")
  Boolean ecsEnabled;

  @Value("${aws.enabled}")
  Boolean awsEnabled;

  @LocalServerPort private int port;

  @MockBean protected AmazonClientProvider mockAwsProvider;

  @DisplayName(".\n===\n" + "Assert AWS and ECS providers are enabled" + "\n===")
  @Test
  public void configTest() {
    assertTrue(awsEnabled);
    assertTrue(ecsEnabled);
    assertEquals("ecs-account", ECS_ACCOUNT_NAME);
  }

  protected String generateStringFromTestFile(String path) throws IOException {
    return new String(Files.readAllBytes(Paths.get(TEST_OPERATIONS_LOCATION, path)));
  }

  protected String generateStringFromTestArtifactFile(String path) throws IOException {
    return new String(Files.readAllBytes(Paths.get(TEST_ARTIFACTS_LOCATION, path)));
  }

  protected String getTestUrl(String path) {
    return "http://localhost:" + port + path;
  }

  protected DefaultCacheResult buildCacheResult(
      Map<String, Object> attributes, String namespace, String key) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(namespace, dataPoints);

    return new DefaultCacheResult(dataMap);
  }

  protected void retryUntilTrue(BooleanSupplier func, String failMsg, int retrySeconds)
      throws InterruptedException {
    for (int i = 0; i < retrySeconds; i++) {
      if (!func.getAsBoolean()) {
        Thread.sleep(1000);
      } else {
        return;
      }
    }
    fail(failMsg);
  }
}
