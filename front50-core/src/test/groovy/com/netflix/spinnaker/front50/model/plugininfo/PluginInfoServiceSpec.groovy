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
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
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

  def "upsert with a new release for an existing plugin"() {
    given:
    PluginInfo currentPluginInfo = new PluginInfo(id: "foo.bar")
    currentPluginInfo.releases.add(new PluginInfo.Release(version: "1.0.0"))

    and:
    PluginInfo newPluginInfo = new PluginInfo(id: "foo.bar")
    newPluginInfo.releases.add(new PluginInfo.Release(version: "2.0.0"))

    when:
    PluginInfo persistedPluginInfo = subject.upsert(newPluginInfo)

    then:
    1 * validator.validate(newPluginInfo, _)
    1 * repository.findById("foo.bar") >> currentPluginInfo
    1 * repository.update("foo.bar", newPluginInfo)
    0 * repository.create(_, _)
    0 * _
    persistedPluginInfo.releases.size() == 2
    persistedPluginInfo.releases*.version == ['1.0.0', '2.0.0']
  }

  def "upsert with an already existing release for an existing plugin"() {
    given:
    PluginInfo currentPluginInfo = new PluginInfo(id: "foo.bar")
    currentPluginInfo.releases.add(new PluginInfo.Release(version: "1.0.0"))

    and:
    PluginInfo newPluginInfo = new PluginInfo(id: "foo.bar")
    newPluginInfo.releases.add(new PluginInfo.Release(version: "1.0.0"))

    when:
    subject.upsert(newPluginInfo)

    then:
    1 * validator.validate(newPluginInfo, _)
    1 * repository.findById("foo.bar") >> currentPluginInfo
    0 * _
    Exception e = thrown(InvalidRequestException)
    e.message == 'Cannot update an existing release: 1.0.0'
  }

  def "creating a new release appends to plugin info releases"() {
    given:
    PluginInfo pluginInfo = new PluginInfo(id: "foo.bar")
    pluginInfo.releases.add(new PluginInfo.Release(version: "1.0.0"))

    PluginInfo.Release newRelease = new PluginInfo.Release(version: "2.0.0")

    when:
    def result = subject.createRelease("foo.bar", newRelease)

    then:
    1 * repository.findById("foo.bar") >> pluginInfo
    1 * validator.validate(pluginInfo,_)
    1 * repository.update("foo.bar", pluginInfo)
    result.releases*.version == ["1.0.0", "2.0.0"]
    0 * _
  }
}
