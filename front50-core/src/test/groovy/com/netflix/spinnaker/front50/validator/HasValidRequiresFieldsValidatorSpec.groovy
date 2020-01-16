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

import com.netflix.spinnaker.front50.model.plugininfo.PluginInfo
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class HasValidRequiresFieldsValidatorSpec extends Specification {

  @Subject
  HasValidRequiresFieldsValidator subject = new HasValidRequiresFieldsValidator()

  @Unroll
  def "requires release with valid requires field formatting"() {
    setup:
    def pluginInfo = new PluginInfo(
      releases: [new PluginInfo.Release(requires: requiresValue)]
    )
    def errors = new GenericValidationErrors(pluginInfo)

    when:
    subject.validate(pluginInfo, errors)

    then:
    errors.hasErrors() == hasErrors

    where:
    requiresValue              || hasErrors
    "gate<=1.0.0,echo>=1.0.0"  || false
    "gate<=1.0.0, echo>=1.0.0" || false
    "gate>=1.0.0"              || false
    "gate<1.0.0"               || false
    "hello-world=1.0.0"        || true
    "gate=1.0.0"               || true
    "gate=foo"                 || true
  }
}
