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
package com.netflix.spinnaker.front50.validator

import com.netflix.spinnaker.front50.model.pluginartifact.PluginArtifact
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HasCanonicalPluginIdValidatorSpec extends Specification {

  @Subject
  HasCanonicalPluginIdValidator subject = new HasCanonicalPluginIdValidator()

  @Unroll
  def "requires a canonical plugin id"() {
    setup:
    PluginArtifact pluginArtifact = new PluginArtifact(id: id)
    Errors errors = new GenericValidationErrors(pluginArtifact)

    when:
    subject.validate(pluginArtifact, errors)

    then:
    errors.hasErrors() == hasErrors

    where:
    id        || hasErrors
    "foo"     || true
    "foo/bar" || true
    "foo.bar" || false
    "."       || true
    ".bar"    || true
    "foo."    || true
  }
}
