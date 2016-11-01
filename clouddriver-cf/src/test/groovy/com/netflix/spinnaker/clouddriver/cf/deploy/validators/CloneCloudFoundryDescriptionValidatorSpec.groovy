/*
 * Copyright 2015 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test cases to verify Cloud Foundry clone validator
 */
class CloneCloudFoundryDescriptionValidatorSpec extends Specification {

  private static final ACCOUNT_NAME = "staging"

  @Shared
  CloneCloudFoundryServerGroupDescriptionValidator validator

  @Shared
  CloudFoundryAccountCredentials cloudFoundryCredentials = TestCredential.named(ACCOUNT_NAME,
    [
        api: 'https://api.example.com',
        org: 'spinnaker',
        space: 'staging'
    ])

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentialsRepo.save(ACCOUNT_NAME, cloudFoundryCredentials)

    validator = new CloneCloudFoundryServerGroupDescriptionValidator(accountCredentialsProvider: credentialsProvider)
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new CloudFoundryDeployDescription(
        application: "foo",
        repository: "s3://repository.com/",
        artifact: "foo.jar",
        credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "null input fails validation"() {
    setup:
    def description = new CloudFoundryDeployDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.credentials.api', _)
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.credentials.org', _)
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.credentials.space', _)
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.application', _)
  }

  void "invalid targetSize fails validation"() {
    setup:
    def description = new CloudFoundryDeployDescription(
        application: "foo",
        repository: "s3://repository.com/",
        artifact: "foo.jar",
        targetSize: 0,
        credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    description.targetSize = 0
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.targetSize', _)

    when:
    description.targetSize = -1
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.targetSize', _)

    when:
    description.targetSize = "foo"

    then:
    thrown(GroovyCastException)

  }

  void "valid targetSize pass validation"() {
    setup:
    def description = new CloudFoundryDeployDescription(
        application: "foo",
        repository: "s3://repository.com/",
        artifact: "foo.jar",
        space: "development", credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    description.targetSize = 1
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)

    when:
    description.targetSize = null
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "templated repository without a trigger should fail"() {
    setup:
    def description = new CloudFoundryDeployDescription(
      application: 'test-app',
      repository: '/{{job}}-{{buildNumber}}',
      artifact: 'test-app-0.0.1-BUILD-SNAPSHOT.war',
      credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('cloneCloudFoundryServerGroupDescription.repository',
        'cloneCloudFoundryServerGroupDescription.repository.templateWithNoParameters')
  }

}
