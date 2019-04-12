/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.echo.pagerduty.PagerDutyService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.JacksonConverter;

import static retrofit.Endpoints.newFixedEndpoint;

@Configuration
@ConditionalOnProperty("pagerDuty.enabled")
public class PagerDutyConfig {
  private static final Logger log = LoggerFactory.getLogger(PagerDutyConfig.class);

  @Bean
  Endpoint pagerDutyEndpoint() {
    return newFixedEndpoint("https://events.pagerduty.com");
  }

  @Bean
  PagerDutyService pagerDutyService(Endpoint pagerDutyEndpoint,
                                    Client retrofitClient,
                                    RestAdapter.LogLevel retrofitLogLevel) {
    log.info("Pager Duty service loaded");

    return new RestAdapter.Builder()
      .setEndpoint(pagerDutyEndpoint)
      .setConverter(new JacksonConverter())
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(PagerDutyService.class))
      .build()
      .create(PagerDutyService.class);
  }
}
