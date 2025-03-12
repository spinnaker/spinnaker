/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.AbstractECSDescription
import spock.lang.Specification
import spock.lang.Subject

abstract class AbstractValidatorSpec extends Specification {
  @Subject
  DescriptionValidator validator = getDescriptionValidator()
  boolean testRegion

  abstract DescriptionValidator getDescriptionValidator()

  abstract AbstractECSDescription getDescription()

  abstract AbstractECSDescription getNulledDescription()

  abstract AbstractECSDescription getInvalidDescription()

  abstract Set<String> notNullableProperties()

  abstract Set<String> invalidProperties()

  abstract String getDescriptionName()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    setTestRegion()
  }

  def setTestRegion(){
    testRegion = true
  }

  void 'should fail an incorrect region'() {
    given:
    def description = getDescription()
    description.credentials = TestCredential.named('test')
    description.region = 'wrong-region-test'
    def errors = Mock(ValidationErrors)

    when:
    if(testRegion) {
      validator.validate([], description, errors)
    }

    then:
    if(testRegion) {
      1 * errors.rejectValue('region', _)
    }
  }

  void 'should fail when required properties are null'() {
    given:
    def description = getNulledDescription()
    def descriptionName = getDescriptionName()
    def errors = Mock(ValidationErrors)
    def nullProperties = notNullableProperties()

    when:
    validator.validate([], description, errors)

    then:
    for(def nullProperty:nullProperties){
      1 * errors.rejectValue(nullProperty, "${descriptionName}.${nullProperty}.not.nullable")
    }
  }

  void 'should fail when a property is invalid'() {
    given:
    def description = getInvalidDescription()
    def descriptionName = getDescriptionName()
    def errors = Mock(ValidationErrors)

    def invalidFields = invalidProperties()

    when:
    validator.validate([], description, errors)

    then:
    for(String invalidField:invalidFields) {
      1 * errors.rejectValue(invalidField, "${descriptionName}.${invalidField}.invalid")
    }
  }

  void 'should pass validation'() {
    given:
    def description = getDescription()
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }
}
