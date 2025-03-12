/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.echo.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.test.config.Retrofit2BasicLogTestConfig;
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {
      Retrofit2TestConfig.class,
      Retrofit2BasicLogTestConfig.class,
    })
public class SlackConfigTest {

  @Autowired OkHttp3ClientConfiguration okHttpClientConfig;

  @Test
  void testSlackServiceConfiguration_noTrailingSlash() {
    SlackLegacyProperties slackLegacyProperties = new SlackLegacyProperties();
    slackLegacyProperties.setBaseUrl(
        "http://localhost/slack"); // baseUrl having one / but not ending with /
    SlackConfig slackConfig = new SlackConfig();
    assertDoesNotThrow(
        () -> slackConfig.slackService(slackLegacyProperties, okHttpClientConfig),
        "Trailing / is handled and hence no exception is thrown");
  }

  @Test
  void testSlackServiceConfiguration_noSlash() {
    SlackLegacyProperties slackLegacyProperties = new SlackLegacyProperties();
    slackLegacyProperties.setBaseUrl("http://localhost");
    SlackConfig slackConfig = new SlackConfig();
    assertDoesNotThrow(
        () -> slackConfig.slackService(slackLegacyProperties, okHttpClientConfig),
        "It's OK for baseUrl to not end with trailing / if there is no / in the middle");
  }
}
