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

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.validator.PluginInfoValidator
import spock.lang.Specification
import spock.lang.Subject

class PluginInfoServiceSpec extends Specification {

  PluginInfoRepository repository = Mock()
  PluginInfoValidator validator = Mock()

  @Subject
  PluginInfoService subject = new PluginInfoService(repository, [validator])

  def "upsert conditionally creates or updates"() {
    given:
    PluginInfo pluginInfo = new PluginInfo(id: "foo.bar")

    when:
    subject.upsert(pluginInfo)

    then:
    1 * validator.validate(pluginInfo, _)
    1 * repository.findById("foo.bar") >> {
      throw new NotFoundException("k")
    }
    1 * repository.create("foo.bar", pluginInfo) >> pluginInfo
    0 * repository.update(_, _)

    when:
    subject.upsert(pluginInfo)

    then:
    1 * validator.validate(pluginInfo, _)
    1 * repository.findById("foo.bar") >> pluginInfo
    1 * repository.update("foo.bar", pluginInfo)
    0 * repository.create(_, _)
  }

  def "creating a new release appends to plugin info releases"() {
    given:
    PluginInfo pluginInfo = new PluginInfo(id: "foo.bar")
    pluginInfo.releases.add(new PluginInfo.Release(version: "1.0.0"))

    PluginInfo.Release newRelease = new PluginInfo.Release(version: "2.0.0")

    when:
    def result = subject.createRelease("foo.bar", newRelease)

    then:
    2 * repository.findById("foo.bar") >> pluginInfo
    result.releases*.version == ["1.0.0", "2.0.0"]
  }
}
