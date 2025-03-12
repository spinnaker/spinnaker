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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.TemplateSource
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class V1TemplateConfigurationSchemaValidatorSpec extends Specification {

  @Subject
  V1TemplateConfigurationSchemaValidator subject = new V1TemplateConfigurationSchemaValidator()

  def "should support version 1"() {
    given:
    def errors = new Errors()
    def templateConfiguration = new TemplateConfiguration(schema: schema)

    when:
    subject.validate(templateConfiguration, errors, new V1TemplateConfigurationSchemaValidator.SchemaValidatorContext([]))

    then:
    if (hasErrors) {
      !errors.hasErrors(true)
    } else {
      errors.hasErrors(true)
    }

    where:
    schema | hasErrors
    null   | true
    "1"    | false
    "2"    | true
  }

  def "should require application name"() {
    given:
    def errors = new Errors()
    def templateConfiguration = new TemplateConfiguration(schema: "1", pipeline: new PipelineDefinition(application: application))

    when:
    subject.validate(templateConfiguration, errors, new V1TemplateConfigurationSchemaValidator.SchemaValidatorContext([]))

    then:
    if (hasErrors) {
      errors.errors[0].message == "Missing 'application' pipeline configuration"
      errors.errors[0].location == 'configuration:pipeline.application'
    } else {
      !errors.hasErrors(true)
    }

    where:
    application | hasErrors
    null        | true
    'myapp'     | false
  }

  @Unroll
  def "should require either dependsOn or inject rule"() {
    given:
    def errors = new Errors()
    def templateConfiguration = new TemplateConfiguration(
      schema: "1",
      pipeline: new PipelineDefinition(
        application: 'myapp',
        template: new TemplateSource()
      ),
      stages: [
        new StageDefinition(
          id: 'foo',
          type: 'foo',
          config: [:]
        )
      ]
    )

    when:
    subject.validate(templateConfiguration, errors, new V1TemplateConfigurationSchemaValidator.SchemaValidatorContext(templateStageIds))

    then:
    if (hasErrors) {
      errors.hasErrors(true)
      errors.errors[0].message == "A configuration-defined stage should have either dependsOn or an inject rule defined"
      errors.errors[0].location == 'configuration:stages.foo'
    } else {
      !errors.hasErrors(true)
    }

    where:
    dependsOn | injectFirst | templateStageIds | hasErrors
    null      | true        | []               | false
    ['bar']   | false       | []               | false
    null      | null        | []               | true
    null      | null        | ["foo"]          | false
  }
}
