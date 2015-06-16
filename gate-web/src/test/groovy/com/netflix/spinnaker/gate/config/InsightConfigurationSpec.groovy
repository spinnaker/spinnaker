package com.netflix.spinnaker.gate.config

import spock.lang.Specification

class InsightConfigurationSpec extends Specification {
  void "should support GString substitution"() {
    expect:
    new InsightConfiguration.Link(
      url: 'http://${hostName}'
    ).applyContext([hostName: "foobar"]).url == 'http://foobar'
  }

  void "should support GString substitution with default value"() {
    expect:
    new InsightConfiguration.Link(
      url: 'http://${hostName ?: "ignored"}'
    ).applyContext([hostName: "foobar"]).url == 'http://foobar'

    new InsightConfiguration.Link(
      url: 'http://${hostName ?: "default"}'
    ).applyContext([:]).url == 'http://default'
  }

  void "should support GString substitution with conditional"() {
    expect:
    new InsightConfiguration.Link(
      url: 'http://${if (publicDnsName) publicDnsName else ""}${if (!publicDnsName) privateDnsName else ""}'
    ).applyContext([publicDnsName: "foobar"]).url == 'http://foobar'

    new InsightConfiguration.Link(
      url: 'http://${if (publicDnsName) publicDnsName else ""}${if (!publicDnsName) privateDnsName else ""}'
    ).applyContext([privateDnsName: "foobar"]).url == 'http://foobar'
  }
}
