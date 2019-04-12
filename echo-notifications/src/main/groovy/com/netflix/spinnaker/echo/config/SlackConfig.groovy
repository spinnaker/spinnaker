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

import com.netflix.spinnaker.echo.slack.SlackClient
import com.netflix.spinnaker.echo.slack.SlackService
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@ConditionalOnProperty('slack.enabled')
@Slf4j
@CompileStatic
class SlackConfig {

  final static String SLACK_INCOMING_WEBHOOK = 'https://hooks.slack.com'
  final static String SLACK_CHAT_API = 'https://slack.com'

  @Bean
  Endpoint slackEndpoint(@Qualifier("useIncomingWebHook") boolean useIncomingWebHook) {
    log.info("Using Slack incoming webhooks: {}.", useIncomingWebHook)
    String endpoint = useIncomingWebHook ? SLACK_INCOMING_WEBHOOK : SLACK_CHAT_API;
    newFixedEndpoint(endpoint)
  }

  @Bean
  SlackService slackService(@Qualifier("useIncomingWebHook") boolean useIncomingWebHook,
                            Endpoint slackEndpoint,
                            Client retrofitClient,
                            RestAdapter.LogLevel retrofitLogLevel) {

    log.info("Slack service loaded")

    def slackClient = new RestAdapter.Builder()
        .setEndpoint(slackEndpoint)
        .setConverter(new JacksonConverter())
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(SlackClient.class))
        .build()
        .create(SlackClient.class)

    new SlackService(slackClient, useIncomingWebHook)
  }

  @Bean(name="useIncomingWebHook")
  boolean useIncomingWebHook(@Value('${slack.token}') String token) {
    return StringUtils.isNotBlank(token) && token.count("/") >= 2
  }

}
