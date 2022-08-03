/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config

import spock.lang.Specification
import spock.lang.Unroll

class CloudDriverConfigurationPropertiesSpec extends Specification {
  @Unroll
  def "supports 0..* read-only base urls"() {
    given:
    def config = new CloudDriverConfigurationProperties(
      clouddriver: new CloudDriverConfigurationProperties.CloudDriver(
        baseUrl: baseUrl,
        readonly: readOnly
      )
    )

    when:
    def baseUrls = config.cloudDriverReadOnlyBaseUrls

    then:
    baseUrls*.baseUrl == expectedBaseUrls

    where:
    baseUrl                 | readOnly                                                        || expectedBaseUrls
    "http://www.google.com" | null                                                            || ["http://www.google.com"]
    "http://www.google.com" | multiBaseUrl(["http://www.foobar.com"])                         || ["http://www.foobar.com"]
    "http://www.google.com" | multiBaseUrl(["http://www.foobar.com", "http://www.yahoo.com"]) || ["http://www.foobar.com", "http://www.yahoo.com"]
  }

  private static CloudDriverConfigurationProperties.MultiBaseUrl multiBaseUrl(List<String> baseUrls) {
    if (baseUrls.size() == 1) {
      return new CloudDriverConfigurationProperties.MultiBaseUrl(baseUrl: baseUrls[0])
    }

    return new CloudDriverConfigurationProperties.MultiBaseUrl(
      baseUrl: null,
      baseUrls: baseUrls.collect { new CloudDriverConfigurationProperties.BaseUrl(it) }
    )
  }
}
