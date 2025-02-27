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

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.echo.slack.SlackAppService
import com.netflix.spinnaker.echo.slack.SlackClient
import com.netflix.spinnaker.echo.slack.SlackService
import com.netflix.spinnaker.echo.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.HttpUrl
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory


@Configuration
@ConditionalOnProperty('slack.enabled')
@EnableConfigurationProperties([SlackLegacyProperties, SlackAppProperties])
@Slf4j
@CompileStatic
class SlackConfig {

  /**
   * This bean is used for integrations with old-style "custom integration" Slack bots, which do not support features
   * like interactive notifications. See {@link #slackAppService} for more details.
   *
   * Slack documentation: https://api.slack.com/custom-integrations
   */
  @Bean
  @Qualifier("slackLegacyService")
  SlackService slackService(@Qualifier("slackLegacyConfig") SlackLegacyProperties config,
                            OkHttp3ClientConfiguration okHttpClientConfig) {

    log.info("Using Slack {}: {}.", config.useIncomingWebhook ? "incoming webhook" : "chat api", config.baseUrl)

    def slackClient =  new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(config.baseUrl))
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(SlackClient.class);

    log.info("Slack legacy service loaded")
    new SlackService(slackClient, config)
  }

  /**
   * This bean is used for new-style Slack apps, which support interactive messages and other features.
   * You can use the same token for both the legacy service above and this one (whose configuration resides
   * in the {@code slack.app} sub-key in the config), if your integration already uses a new app, or a different
   * token for the legacy service and this one, which might be useful for migrations.
   *
   * Calls to {@code POST /notifications} for non-interactive notifications rely on the legacy service, and
   * on the app service for interactive ones. {@see NotificationController#create}
   */
  @Bean
  @Qualifier("slackAppService")
  SlackAppService slackAppService(@Qualifier("slackAppConfig") SlackAppProperties config,
                                  OkHttp3ClientConfiguration okHttpClientConfig) {
    def slackClient = new Retrofit.Builder()
      .baseUrl(RetrofitUtils.getBaseUrl(config.baseUrl))
      .client(okHttpClientConfig.createForRetrofit2().build())
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .addConverterFactory(JacksonConverterFactory.create())
      .build()
      .create(SlackClient.class);

    log.info("Slack app service loaded")
    new SlackAppService(slackClient, config)
  }
}
