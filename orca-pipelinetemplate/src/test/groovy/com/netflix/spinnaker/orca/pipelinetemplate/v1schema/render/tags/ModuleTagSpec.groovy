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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateModule
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Subject

class ModuleTagSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  Renderer renderer = new JinjaRenderer(objectMapper, Mock(Front50Service), [])

  @Subject
  ModuleTag subject = new ModuleTag(renderer, objectMapper)

  def 'should render module'() {
    given:
    PipelineTemplate pipelineTemplate = new PipelineTemplate(
      modules: [
        new TemplateModule(
          id: 'myModule',
          variables: [
            [name: 'myStringVar', defaultValue: 'hello'] as NamedHashMap,
            [name: 'myOtherVar'] as NamedHashMap,
            [name: 'subject'] as NamedHashMap,
            [name: 'job'] as NamedHashMap,
            [name: 'concat', type: 'object'] as NamedHashMap,
            [name: 'filtered'] as NamedHashMap
          ],
          definition: '{{myStringVar}} {{myOtherVar}}, {{subject}}. You triggered {{job}} {{concat}} {{filtered}}')
      ]
    )
    RenderContext context = new DefaultRenderContext('myApp', pipelineTemplate, [job: 'myJob', buildNumber: 1234])
    context.variables.put("testerName", "Mr. Tester Testington")
    context.variables.put("m", [myKey: 'myValue'])

    when:
    def result = renderer.render("{% module myModule myOtherVar=world, subject=testerName, job=trigger.job, concat=m['my' + 'Key'], filtered=trigger.nonExist|default('hello', True) %}", context)

    then:
    result == 'hello world, Mr. Tester Testington. You triggered myJob myValue hello'
  }
}
