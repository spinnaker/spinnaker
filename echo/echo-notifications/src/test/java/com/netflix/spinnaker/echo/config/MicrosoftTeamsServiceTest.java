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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsService;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsTemplateEngine;
import com.netflix.spinnaker.echo.test.config.Retrofit2BasicLogTestConfig;
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(
    classes = {
      MicrosoftTeamsConfig.class,
      Retrofit2TestConfig.class,
      Retrofit2BasicLogTestConfig.class,
      MicrosoftTeamsServiceTest.TestConfig.class
    },
    properties = "microsoftteams.enabled=true")
public class MicrosoftTeamsServiceTest {

  @Autowired private MicrosoftTeamsService microsoftTeamsService;

  @Autowired private MicrosoftTeamsTemplateEngine microsoftTeamsTemplateEngine;

  @Test
  void testMicrosoftTeamsServiceConfiguration() {
    assertNotNull(microsoftTeamsService);
  }

  @Test
  void testMicrosoftTeamsTemplateEngineConfiguration() {
    assertNotNull(microsoftTeamsTemplateEngine);
  }

  @Configuration
  static class TestConfig {
    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
