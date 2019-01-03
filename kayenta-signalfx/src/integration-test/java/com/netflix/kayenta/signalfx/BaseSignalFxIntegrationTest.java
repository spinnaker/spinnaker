/*
 * Copyright (c) 2018 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.signalfx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.Main;
import com.netflix.kayenta.canary.CanaryConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;

@RunWith(SpringRunner.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = Main.class
)
@Slf4j
public abstract class BaseSignalFxIntegrationTest {

  protected static final String METRICS_ACCOUNT_NAME_QUERY_KEY = "metricsAccountName";
  protected static final String STORAGE_ACCOUNT_NAME_QUERY_KEY = "storageAccountName";
  protected static final String METRICS_ACCOUNT_NAME = "sfx-integration-test-account";
  protected static final String STORAGE_ACCOUNT_NAME = "in-memory-store";
  protected static final String TEST_ID = "test-id";
  protected static final String LOCATION = "us-west-2";

  protected CanaryConfig integrationTestCanaryConfig;

  @Autowired
  protected ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  protected String testId;

  @Autowired
  protected Instant metricsReportingStartTime;

  @LocalServerPort
  protected int serverPort;

  protected String getUriTemplate() {
    return "http://localhost:" + serverPort + "%s";
  }

  @Before
  public void before() {
    try {
      integrationTestCanaryConfig = objectMapper.readValue(getClass().getClassLoader()
          .getResourceAsStream("integration-test-canary-config.json"), CanaryConfig.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load SignalFx integration test canary config");
    }
  }
}
