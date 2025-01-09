package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.slack.SlackAppService
import com.netflix.spinnaker.echo.slack.SlackService
import com.netflix.spinnaker.echo.test.config.Retrofit2BasicLogTestConfig
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import spock.lang.Subject

@SpringBootTest(
  classes = [SlackConfig, Retrofit2TestConfig, Retrofit2BasicLogTestConfig],
  properties = [
    "slack.enabled = true",
    // Used for the old bot
    "slack.token = xoxb-token-for-old-bot",
    // Used for the new bot
    "slack.app.token = xoxb-token-for-new-bot",
    "slack.app.verification_token = verification-token",
    "slack.app.signing_secret = signing-secret"
  ],
  webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class SlackConfigSpec extends Specification {
  @Subject
  @Autowired
  @Qualifier("slackLegacyConfig")
  SlackLegacyProperties legacyConfig

  @Subject
  @Autowired
  @Qualifier("slackAppConfig")
  SlackAppProperties appConfig

  @Subject
  @Autowired
  @Qualifier("slackLegacyService")
  SlackService slackLegacyService

  @Subject
  @Autowired
  @Qualifier("slackAppService")
  SlackAppService slackAppService

  def 'legacy config loads as expected'() {
    expect:
    legacyConfig.token == "xoxb-token-for-old-bot"
  }

  def 'test slack incoming web hook is inferred correctly'() {
    given:

    when:
    legacyConfig.token = token

    then:
    legacyConfig.useIncomingWebhook == expectedUseIncomingWebHook
    legacyConfig.baseUrl == expectedEndpoint

    where:
    token                                          | expectedEndpoint                   | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://slack.com"                | false
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://hooks.slack.com/services" | true
    "OLD/FASHION"                                  | "https://slack.com"                | false
    ""                                             | "https://slack.com"                | false
    null                                           | "https://slack.com"                | false
  }

  def 'test slack incoming web hook base url is defined'() {
    given:

    when:
    legacyConfig.token = token
    legacyConfig.baseUrl = baseUrl

    then:
    legacyConfig.useIncomingWebhook == expectedUseIncomingWebHook
    legacyConfig.baseUrl == expectedEndpoint

    where:
    token                                          | baseUrl               | expectedEndpoint                   | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://example.com" | "https://example.com"              | false
    "myOldFashionToken"                            | ""                    | "https://slack.com"                | false
    "myOldFashionToken"                            | null                  | "https://slack.com"                | false
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://example.com" | "https://example.com"              | true
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | ""                    | "https://hooks.slack.com/services" | true
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | null                  | "https://hooks.slack.com/services" | true
  }

  def 'test slack incoming web hook when forceUseIncomingWebhook'() {
    given:

    when:
    legacyConfig.token = token
    legacyConfig.forceUseIncomingWebhook = true
    legacyConfig.baseUrl = 'https://example.com'

    then:
    legacyConfig.useIncomingWebhook == expectedUseIncomingWebHook
    legacyConfig.baseUrl == expectedEndpoint

    where:
    token                                          | expectedEndpoint      | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://example.com" | true
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://example.com" | true
    "OLD/FASHION"                                  | "https://example.com" | true
    ""                                             | "https://example.com" | true
    null                                           | "https://example.com" | true
  }

  def 'new app config loads as expected'() {
    expect:
    appConfig.token == "xoxb-token-for-new-bot"
    appConfig.verificationToken == "verification-token"
    appConfig.signingSecret == "signing-secret"
  }

  def 'legacy and new app services use different configs and clients'() {
    expect:
    slackAppService.config != slackLegacyService.config
    slackAppService.slackClient != slackLegacyService.slackClient
  }

}

