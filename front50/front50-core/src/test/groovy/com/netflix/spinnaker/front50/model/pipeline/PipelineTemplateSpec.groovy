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
package com.netflix.spinnaker.front50.model.pipeline

import spock.lang.Specification
import spock.lang.Unroll

class PipelineTemplateSpec extends Specification {

  @Unroll
  def 'should match scopes'() {
    given:
    def template = new PipelineTemplate(metadata: [scopes: templateScopes])

    expect:
    shouldMatch == template.containsAnyScope(requestedScopes)

    where:
    templateScopes  | requestedScopes       || shouldMatch
    ['global']      | ['global']            || true
    ['spinnaker.*'] | ['spinnaker_front50'] || true
    ['front50']     | ['FRONT50']           || true
  }

  def 'should be ok if template has no scopes'() {
    given:
    def template = new PipelineTemplate(metadata: [description: 'I have no scopes'])

    when:
    template.containsAnyScope(['global'])

    then:
    notThrown(NullPointerException)
  }

}
