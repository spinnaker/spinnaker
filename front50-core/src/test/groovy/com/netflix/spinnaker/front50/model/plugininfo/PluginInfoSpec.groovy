/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.front50.model.plugininfo


import spock.lang.Specification
import spock.lang.Unroll

class PluginInfoSpec extends Specification {

  @Unroll
  def "plugin info release supports service"() {
    given:
    def release = new PluginInfo.Release(
      requires: [
        "clouddriver>=2.0.0",
        "orca>7.0.0",
        "gate>=3.0.0"
      ]
    )

    expect:
    release.supportsService(service) == expectedResult

    where:
    service       || expectedResult
    "gate"        || true
    "echo"        || false
    "clouddriver" || true
  }
}
