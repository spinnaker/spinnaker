/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.web.selector.v2

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.kork.web.selector.v2.SelectableService.*

class SelectableServiceSpec extends Specification {
  @Shared
  def altBakeryService = new TestService(name: "altBakeryService")

  @Shared
  def bakery = new TestService(name: "bakery")

  @Unroll
  def "should lookup service by parameters"() {
    given:
    def baseUrls = [
      new BaseUrl(
        baseUrl: "http://bakery.com",
        priority: 1,
        config: [altApiEbabled: false],
        parameters: Parameter.toParameters([
          [
            name: "cloudProvider",
            values: ["aws", "titus"]
          ],
          [
            name: "authenticatedUser",
            values: ["regex:.+@netflix.com\$"]
          ]
        ])
      ),
      new BaseUrl(
        baseUrl: "http://altBakeryService.com",
        priority: 2,
        config: [altApiEbabled: true],
        parameters: Parameter.toParameters([
          [
            name: "OS",
            values: ["windows", "centOS"]
          ],
          [
            name: "authenticatedUser",
            values: ["regex:.+@company.com\$"]
          ]
        ])
      )
    ]

    def defaultService = bakery
    def defaultConfig = [cores: 10]
    def selectable = new SelectableService<TestService>(baseUrls, defaultService, defaultConfig, { url -> getService(url) })

    when:
    def selectedService = selectable.byParameters(criteria)

    then:
    selectedService.service == expectedService
    selectedService.config == config

    where:
    criteria                                                                  || config                             ||  expectedService
    [new Parameter(name: "OS", values: ["centOS"])]                           || [cores: 10]                        ||  bakery

    [
      new Parameter(name: "OS", values: ["centOS"]),
      new Parameter(name: "authenticatedUser", values: ["bob@company.com"])
    ]                                                                         || [cores: 10, altApiEbabled: true]   ||  altBakeryService

    [new Parameter(name: "cloudProvider", values: ["titus"])]                 || [cores: 10]                        ||  bakery

    [
      new Parameter(name: "cloudProvider", values: ["titus"]),
      new Parameter(name: "authenticatedUser", values: ["blah@netflix.com"])
    ]                                                                         || [cores: 10, altApiEbabled: false]  ||  bakery

    [new Parameter(name: "OS", values: ["macOS"])]                            || [cores: 10]                        ||  bakery

    [
      new Parameter(name: "OS", values: ["macOS"]),
      new Parameter(name: "authenticatedUser", values: ["bob@company.com"])
    ]                                                                         || [cores: 10]                        ||  bakery
  }

  @Unroll
  def "should shard service by authenticated users"() {
    given:
    def baseUrls = [
      new BaseUrl(
        baseUrl: "http://bakery.com",
        priority: 1,
        config: [logLevel: "DEBUG"],
        parameters: [new Parameter("authenticatedUser", ["regex:^[a...e].+@netflix.com\$"])]
      ),
      new BaseUrl(
        baseUrl: "http://bakery.com",
        priority: 2,
        config: [logLevel: "INFO"],
        parameters: [new Parameter("authenticatedUser", ["regex:^[e...z].+@netflix.com\$"])]
      ),
      new BaseUrl(
        baseUrl: "http://altBakeryService.com",
        priority: 3,
        config: [logLevel: "WARN"],
        parameters: [new Parameter("authenticatedUser", ["anonymous", "regex:.+@company.com\$"])]
      )
    ]

    def defaultService = bakery
    def defaultConfig = [appender: "Stdout"]
    def selectable = new SelectableService<TestService>(baseUrls, defaultService, defaultConfig, { url -> getService(url) })

    when:
    def selectedService = selectable.byParameters(criteria)

    then:
    selectedService.service == expectedService
    selectedService.config == config

    where:
    criteria                                                                                        || config                                   ||  expectedService
    [new Parameter(name: "authenticatedUser", values: ["alice@netflix.com"])]                       || [appender: "Stdout", logLevel: "DEBUG"]  || bakery // this service is configured for user names starting with a through e
    [new Parameter(name: "authenticatedUser", values: ["test@company.com"])]                        || [appender: "Stdout", logLevel: "WARN"]   || altBakeryService // matches anything ending with @company
    [new Parameter(name: "authenticatedUser", values: ["zaire@netflix.com"])]                       || [appender: "Stdout", logLevel: "INFO"]   || bakery // matches service for users names starting with e through z
    [new Parameter(name: "authenticatedUser", values: ["zaire@netflix.com", "alice@netflix.com"])]  || [appender: "Stdout", logLevel: "DEBUG"]  || bakery // zaire should fall under the 2nd service but alice enforces the first service because of higher priority
    [new Parameter(name: "authenticatedUser", values: ["rodolfo@yahoo.com"])]                       || [appender: "Stdout"]                     || bakery // no match, fallback on default service
  }

  static class TestService {
    String name
  }

  def getService(String url) {
    if (url == "http://bakery.com") {
      return bakery
    }

    return altBakeryService
  }
}
