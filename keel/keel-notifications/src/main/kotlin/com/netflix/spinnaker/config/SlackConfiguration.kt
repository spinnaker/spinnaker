package com.netflix.spinnaker.config

import com.slack.api.bolt.App
import com.slack.api.bolt.AppConfig
import com.slack.api.bolt.middleware.Middleware
import com.slack.api.bolt.middleware.builtin.RequestVerification
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty("slack.enabled")
@ConfigurationProperties(prefix = "slack")
class SlackConfiguration {
  var token: String? = null
  var defaultEmailDomain: String? = null
}

@Configuration
class SlackBotConfiguration {

  @Bean
  fun appConfig(config: SlackConfiguration): AppConfig {
    return AppConfig.builder()
      .singleTeamBotToken(config.token)
      .scope("commands,chat:write")
      .classicAppPermissionsEnabled(true)
      .build()
  }

  @Bean
  fun slackApp(appConfig: AppConfig): UnprotectedApp {
    return UnprotectedApp(appConfig)
  }

}

/**
 * This is a workaround to disable signature verification,
 * because we are using Netflix's Wall-e (Zuul) as a proxy in front of Spinnaker and it already checks the Slack signatures.
 * See https://github.com/slackapi/java-slack-sdk/issues/689
 */
class UnprotectedApp(appConfig: AppConfig) : App(appConfig) {
  override fun buildDefaultMiddlewareList(appConfig: AppConfig): List<Middleware> {
    return super.buildDefaultMiddlewareList(appConfig)
      .filter { it !is RequestVerification }
  }
}
