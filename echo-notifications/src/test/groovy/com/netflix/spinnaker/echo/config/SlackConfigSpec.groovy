package com.netflix.spinnaker.echo.config

import spock.lang.Specification
import spock.lang.Subject

class SlackConfigSpec extends Specification {
  @Subject SlackConfig slackConfig = new SlackConfig()

  def 'test slack incoming web hook is inferred correctly'() {
    given:

    when:
    def useIncomingHook = slackConfig.useIncomingWebHook(token)
    def endpoint = slackConfig.slackEndpoint(useIncomingHook)

    then:
    useIncomingHook == expectedUseIncomingWebHook
    endpoint.url == expectedEndpoint

    where:
    token                                          | expectedEndpoint          | expectedUseIncomingWebHook
    "myOldFashionToken"                            | "https://slack.com"       | false
    "T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX" | "https://hooks.slack.com" | true
    "OLD/FASHION"                                  | "https://slack.com"       | false
    ""                                             | "https://slack.com"       | false
    null                                           | "https://slack.com"       | false
  }
}
