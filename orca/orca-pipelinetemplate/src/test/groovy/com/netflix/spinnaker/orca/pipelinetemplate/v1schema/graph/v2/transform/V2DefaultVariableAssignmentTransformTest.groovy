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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration
import spock.lang.Specification
import spock.lang.Unroll

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

  def "configurationVariables assign default value for config vars present in template vars but not assigned in config vars"() {
    when:
        def actualConfigVars = V2DefaultVariableAssignmentTransform.configurationVariables(templateVars, configVars)

    then:
        actualConfigVars == expectedVars

    where:
        templateVars                        | configVars                   | expectedVars
        [newTemplateVar("wait", "DEFAULT")] | [wait: "OK"]                 | [wait: "OK"]
        []                                  | [wait: "OK"]                 | [:]
        [newTemplateVar("wait", "DEFAULT")] | [alsoWait: "NO"]             | [wait: "DEFAULT"]
        [newTemplateVar("wait")]            | [:]                          | [:]
  }

  V2PipelineTemplate.Variable newTemplateVar(String name) {
    def var = new V2PipelineTemplate.Variable()
    var.name = name
    return var
  }

  V2PipelineTemplate.Variable newTemplateVar(String name, String defaultValue) {
    def var = new V2PipelineTemplate.Variable()
    var.name = name
    var.defaultValue = defaultValue
    return var
  }

  def "error message should include pipeline config context with all fields"() {
    given:
    def template = new V2PipelineTemplate(
      variables: [
        newTemplateVarWithType("count", "int")
      ]
    )

    def configuration = new V2TemplateConfiguration(
      application: "my-application",
      name: "my-pipeline",
      pipelineConfigId: "abc-123-def",
      variables: [
        count: "not-an-int"  // String instead of int
      ]
    )

    def subject = new V2DefaultVariableAssignmentTransform(configuration)

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

  def "error message should include pipeline config context with only application"() {
    given:
    def template = new V2PipelineTemplate(
      variables: [
        newTemplateVarWithType("count", "int")
      ]
    )

    def configuration = new V2TemplateConfiguration(
      application: "only-app",
      variables: [
        count: "not-an-int"
      ]
    )

    def subject = new V2DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config: application='only-app']")
  }

  def "error message should include pipeline config context with only id"() {
    given:
    def template = new V2PipelineTemplate(
      variables: [
        newTemplateVarWithType("count", "int")
      ]
    )

    def configuration = new V2TemplateConfiguration(
      pipelineConfigId: "only-id-123",
      variables: [
        count: "not-an-int"
      ]
    )

    def subject = new V2DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config: id='only-id-123']")
  }

  def "error message should show 'unknown' when no context fields are available"() {
    given:
    def template = new V2PipelineTemplate(
      variables: [
        newTemplateVarWithType("count", "int")
      ]
    )

    def configuration = new V2TemplateConfiguration(
      variables: [
        count: "not-an-int"
      ]
    )

    def subject = new V2DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config: unknown]")
  }

  @Unroll
  def "error message includes context for type mismatch: expected #expectedType, got #actualValue"() {
    given:
    def template = new V2PipelineTemplate(
      variables: [
        newTemplateVarWithType("testVar", expectedType)
      ]
    )

    def configuration = new V2TemplateConfiguration(
      application: "test-app",
      name: "test-pipeline",
      variables: [
        testVar: actualValue
      ]
    )

    def subject = new V2DefaultVariableAssignmentTransform(configuration)

    when:
    subject.visitPipelineTemplate(template)

    then:
    def ex = thrown(IllegalTemplateConfigurationException)
    ex.message.contains("[Pipeline Config:")
    ex.message.contains("application='test-app'")
    ex.message.contains("testVar")

    where:
    expectedType | actualValue
    "int"        | "string-value"
    "bool"       | "not-a-boolean"
    "list"       | "not-a-list"
  }

  V2PipelineTemplate.Variable newTemplateVarWithType(String name, String type) {
    def var = new V2PipelineTemplate.Variable()
    var.name = name
    var.type = type
    return var
  }
}
