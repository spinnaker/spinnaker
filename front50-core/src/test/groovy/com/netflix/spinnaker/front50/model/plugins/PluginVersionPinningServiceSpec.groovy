/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model.plugins

import spock.lang.Specification
import spock.lang.Subject

class PluginVersionPinningServiceSpec extends Specification {

  PluginVersionPinningRepository versionRepository = Mock()
  PluginInfoRepository infoRepository = Mock()

  @Subject PluginVersionPinningService subject = new PluginVersionPinningService(versionRepository, infoRepository)

  def "pins versions"() {
    given:
    def serviceName = "orca"
    def serverGroupName = "orca-v000"
    def location = "us-west-2"
    def id = "$serviceName-$serverGroupName-$location"
    def versions = [
      "foo": "1.0.0",
      "bar": "1.0.0"
    ]

    def result

    when:
    result = subject.pinVersions(serviceName, serverGroupName, "us-west-2", versions)

    then:
    result.foo.version == "1.0.0"
    result.bar.version == "1.0.0"
    1 * versionRepository.findById(id) >> null
    1 * versionRepository.create(id, _)
    1 * infoRepository.findById("foo") >> pluginInfo("foo", "1.0.0")
    1 * infoRepository.findById("bar") >> pluginInfo("bar", "1.0.0")
    0 * _

    when:
    versions.foo = "1.0.1"
    result = subject.pinVersions(serviceName, serverGroupName, location, versions)

    then:
    result.foo.version == "1.0.0"
    result.foo.version == "1.0.0"
    1 * versionRepository.findById(id) >> new ServerGroupPluginVersions(
      id,
      serverGroupName,
      location,
      [
        foo: "1.0.0",
        bar: "1.0.0"
      ]
    )
    1 * infoRepository.findById("foo") >> pluginInfo("foo", "1.0.0")
    1 * infoRepository.findById("bar") >> pluginInfo("bar", "1.0.0")
    0 * _
  }

  private static PluginInfo pluginInfo(String id, String version) {
    new PluginInfo(id: id).tap {
      releases.add(new PluginInfo.Release(version: version))
    }
  }
}
