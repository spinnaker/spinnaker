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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineDefinition
import spock.lang.Specification

class DefaultVariableAssignmentTransformSpec extends Specification {

  def 'should assign default variables if configuration defines none'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'foo', defaultValue: 'fooDefaultValue'),
        new Variable(name: 'bar'),
        new Variable(name: 'baz', defaultValue: 'bazDefaultValue')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        variables: [
          bar: 'barValue',
          baz: 'bazValue'
        ]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    configuration.pipeline.variables.with {
      it['foo'] == 'fooDefaultValue'
      it['bar'] == 'barValue'
      it['baz'] == 'bazValue'
    }
  }

  def 'should throw template configuration exception if required variable is undefined'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'bar')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        variables: [:]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    thrown(IllegalTemplateConfigurationException)
  }
}
