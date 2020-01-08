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
package com.netflix.spinnaker.front50.model.pluginartifact

import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.validator.PluginArtifactValidator
import spock.lang.Specification
import spock.lang.Subject

class PluginArtifactServiceSpec extends Specification {

  PluginArtifactRepository repository = Mock()
  PluginArtifactValidator validator = Mock()

  @Subject
  PluginArtifactService subject = new PluginArtifactService(repository, [validator])

  def "upsert conditionally creates or updates"() {
    given:
    PluginArtifact pluginArtifact = new PluginArtifact(id: "foo.bar")

    when:
    subject.upsert(pluginArtifact)

    then:
    1 * validator.validate(pluginArtifact, _)
    1 * repository.findById("foo.bar") >> {
      throw new NotFoundException("k")
    }
    1 * repository.create("foo.bar", pluginArtifact) >> pluginArtifact
    0 * repository.update(_, _)

    when:
    subject.upsert(pluginArtifact)

    then:
    1 * validator.validate(pluginArtifact, _)
    1 * repository.findById("foo.bar") >> pluginArtifact
    1 * repository.update("foo.bar", pluginArtifact)
    0 * repository.create(_, _)
  }

  def "creating a new release appends to plugin artifact releases"() {
    given:
    PluginArtifact pluginArtifact = new PluginArtifact(id: "foo.bar")
    pluginArtifact.releases.add(new PluginArtifact.Release(version: "1.0.0"))

    PluginArtifact.Release newRelease = new PluginArtifact.Release(version: "2.0.0")

    when:
    def result = subject.createRelease("foo.bar", newRelease)

    then:
    2 * repository.findById("foo.bar") >> pluginArtifact
    result.releases*.version == ["1.0.0", "2.0.0"]
  }
}
