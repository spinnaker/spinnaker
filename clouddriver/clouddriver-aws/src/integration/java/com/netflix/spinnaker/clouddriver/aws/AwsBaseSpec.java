/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws;

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.fail;

import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import io.restassured.http.ContentType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Base class with common config and helper methods for test classes */
@Import(AwsTestConfiguration.class)
@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
public abstract class AwsBaseSpec {

  @Value("${aws.primaryAccount}")
  protected String AWS_ACCOUNT_NAME;

  @Value("${aws.enabled}")
  protected Boolean AWS_ENABLED;

  @MockBean protected AmazonClientProvider mockAwsClientProvider;
  @MockBean protected RegionScopedProviderFactory.RegionScopedProvider mockRegionScopedProvider;
  @MockBean protected Front50Service mockFront50Service;

  @LocalServerPort int port;

  protected final int TASK_RETRY_SECONDS = 3;

  protected static final String PATH_PREFIX = "classpath:testinputs/";
  protected static final String GET_TASK_PATH = "/task/";
  protected static final String EXPECTED_DEPLOY_SUCCESS_MSG = "Deployed EC2 server group";

  protected static final String CREATE_SERVER_GROUP_OP_PATH = "/aws/ops/createServerGroup";
  protected static final String UPDATE_LAUNCH_TEMPLATE_OP_PATH = "/aws/ops/updateLaunchTemplate";

  protected String getBaseUrl() {
    return "http://localhost:" + port;
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

  protected String getTaskUpdatesAfterCompletion(String taskId) throws InterruptedException {
    AtomicReference<String> taskHistoryToRet = new AtomicReference<>();
    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getBaseUrl() + GET_TASK_PATH + taskId)
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");

          // try until the response indicates that orchestration has completed or failed
          if (!taskHistory.toString().contains("Orchestration completed")
              && !taskHistory.toString().contains("Orchestration failed")) {
            return false;
          }

          taskHistoryToRet.set(taskHistory.toString());
          return true;
        },
        String.format(
            "Failed to retrieve all task updates from task response in %s seconds.",
            TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    return taskHistoryToRet.get();
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
