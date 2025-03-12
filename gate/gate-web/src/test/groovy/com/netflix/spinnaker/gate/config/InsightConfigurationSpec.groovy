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

  void "should filter on cloudProvider"() {
    setup:
    def link = new InsightConfiguration.Link(url: 'http://providerLink', cloudProvider: 'aws')

    expect:
    link.applyContext([:]) == null
    link.applyContext([cloudProvider: 'titus']) == null
    link.applyContext([cloudProvider: 'aws']).url == 'http://providerLink'
  }
}
