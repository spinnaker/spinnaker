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

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateSchemaValidator.SchemaValidatorContext
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class V1TemplateSchemaValidatorSpec extends Specification {

  @Subject
  V1TemplateSchemaValidator subject = new V1TemplateSchemaValidator()

  def "should support version 1"() {
    given:
    def errors = new Errors()
    def template = new PipelineTemplate(schema: schema)

    when:
    subject.validate(template, errors, new SchemaValidatorContext(false))

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

  @Unroll
  def "should error if configuration defines stages when template is protected"() {
    given:
    def errors = new Errors()
    def template = new PipelineTemplate(schema: '1', protect: true)

    when:
    subject.validate(template, errors, new SchemaValidatorContext(hasStages))

    then:
    if (hasStages) {
      errors.hasErrors(true)
    } else {
      !errors.hasErrors(true)
    }

    where:
    hasStages << [true, false]
  }
}
