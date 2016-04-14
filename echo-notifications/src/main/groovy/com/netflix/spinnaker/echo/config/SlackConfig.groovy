/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.echo.config
import com.netflix.spinnaker.echo.slack.SlackService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@ConditionalOnProperty('slack.enabled')
@Slf4j
@CompileStatic
class SlackConfig {

  @Bean
  Endpoint slackEndpoint() {
    newFixedEndpoint('https://slack.com')
  }

  @Bean
  SlackService slackService(Endpoint slackEndpoint, Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {

    log.info("slack service loaded")

    new RestAdapter.Builder()
        .setEndpoint(slackEndpoint)
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .build()
        .create(SlackService)
  }

}
