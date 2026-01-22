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
import spock.lang.Unroll

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

  def 'error message should include pipeline config context with all fields'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'count', type: 'int')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        application: 'my-application',
        name: 'my-pipeline',
        pipelineConfigId: 'abc-123-def',
        variables: [
          count: 'not-an-int'  // String instead of int
        ]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config:")
    ex.message.contains("application='my-application'")
    ex.message.contains("name='my-pipeline'")
    ex.message.contains("id='abc-123-def'")
    ex.message.contains("count")
    ex.message.contains("expected type 'int'")
    ex.message.contains("found type 'String'")
  }

  def 'error message should include pipeline config context with only application'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'count', type: 'int')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        application: 'only-app',
        variables: [
          count: 'not-an-int'
        ]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config: application='only-app']")
  }

  def 'error message should include pipeline config context with only id'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'count', type: 'int')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        pipelineConfigId: 'only-id-123',
        variables: [
          count: 'not-an-int'
        ]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config: id='only-id-123']")
  }

  def 'error message should show unknown when no context fields are available'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'count', type: 'int')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        variables: [
          count: 'not-an-int'
        ]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config: unknown]")
  }

  def 'error message for missing variable should include pipeline config context'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'requiredVar')
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        application: 'test-app',
        name: 'test-pipeline',
        pipelineConfigId: 'test-id',
        variables: [:]  // Missing required variable
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config:")
    ex.message.contains("application='test-app'")
    ex.message.contains("name='test-pipeline'")
    ex.message.contains("id='test-id'")
    ex.message.contains("requiredVar")
  }

  @Unroll
  def 'error message includes context for type mismatch: expected #expectedType, got #actualValue'() {
    given:
    def template = new PipelineTemplate(
      variables: [
        new Variable(name: 'testVar', type: expectedType)
      ]
    )

    def configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        application: 'test-app',
        name: 'test-pipeline',
        variables: [
          testVar: actualValue
        ]
      )
    )

    def subject = new DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config:")
    ex.message.contains("application='test-app'")
    ex.message.contains("testVar")

    where:
    expectedType | actualValue
    'int'        | 'string-value'
    'bool'       | 'not-a-boolean'
    'list'       | 'not-a-list'
  }
}
