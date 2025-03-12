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
    baseUrl                   | readOnly                                                                           || expectedBaseUrls
    "http://clouddriver:7002" | null                                                                               || ["http://clouddriver:7002"]
    "http://clouddriver:7002" | multiBaseUrl(["http://clouddriver-read:7002"])                                     || ["http://clouddriver-read:7002"]
    "http://clouddriver:7002" | multiBaseUrl(["http://clouddriver-read-1:7002", "http://clouddriver-read-2:7002"]) || ["http://clouddriver-read-1:7002", "http://clouddriver-read-2:7002"]
  }

  @Unroll
  def "supports 0..* write-only base urls"() {
    given:
    def config = new CloudDriverConfigurationProperties(
        clouddriver: new CloudDriverConfigurationProperties.CloudDriver(
            baseUrl: baseUrl,
            writeonly: writeOnly
        )
    )

    when:
    def baseUrls = config.cloudDriverWriteOnlyBaseUrls

    then:
    baseUrls*.baseUrl == expectedBaseUrls

    where:
    baseUrl                   | writeOnly                                                                            || expectedBaseUrls
    "http://clouddriver:7002" | null                                                                                 || ["http://clouddriver:7002"]
    "http://clouddriver:7002" | multiBaseUrl(["http://clouddriver-write:7002"])                                      || ["http://clouddriver-write:7002"]
    "http://clouddriver:7002" | multiBaseUrl(["http://clouddriver-write-1:7002", "http://clouddriver-write-2:7002"]) || ["http://clouddriver-write-1:7002", "http://clouddriver-write-2:7002"]
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
