/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.transform

import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate
import spock.lang.Specification

class V2DefaultVariableAssignmentTransformTest extends Specification {
  def "configurationVariables filters config vars not present in template vars"() {
    when:
    def actualConfigVars = V2DefaultVariableAssignmentTransform.configurationVariables(templateVars, configVars)

    then:
    actualConfigVars == expectedVars

    where:
    templateVars             | configVars                   | expectedVars
    [newTemplateVar("wait")] | [wait: "OK"]                 | [wait: "OK"]
    []                       | [wait: "OK"]                 | [:]
    [newTemplateVar("wait")] | [wait: "OK", alsoWait: "NO"] | [wait: "OK"]
    [newTemplateVar("wait")] | [:]                          | [:]
  }

  V2PipelineTemplate.Variable newTemplateVar(String name) {
    def var = new V2PipelineTemplate.Variable()
    var.name = name
    return var
  }
}
