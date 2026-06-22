/*
 * Copyright 2020 Cerner Corporation
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

package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsService;
import com.netflix.spinnaker.echo.microsoftteams.MicrosoftTeamsTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("microsoftteams.enabled")
@EnableConfigurationProperties(MicrosoftTeamsProperties.class)
@Slf4j
public class MicrosoftTeamsConfig {

  @Autowired private MicrosoftTeamsProperties microsoftTeamsProperties;

  @Bean
  public MicrosoftTeamsService microsoftTeamsService(
      OkHttp3ClientConfiguration okHttp3ClientConfiguration) {
    log.info("Microsoft Teams service loaded");

    return new MicrosoftTeamsService(okHttp3ClientConfiguration);
  }

  @Bean
  public MicrosoftTeamsTemplateEngine microsoftTeamsTemplateEngine(ObjectMapper objectMapper) {
    log.info(
        "Microsoft Teams template engine loaded with template path: {}",
        microsoftTeamsProperties.getTemplatePath());

    return new MicrosoftTeamsTemplateEngine(
        objectMapper, microsoftTeamsProperties.getTemplatePath());
  }
}
