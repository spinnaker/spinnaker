/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.kato.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kato.cf.TestCredential
import com.netflix.spinnaker.kato.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.springframework.validation.Errors
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test cases to verify Cloud Foundry deploy validator
 *
 *
 */
class CloudFoundryDeployDescriptionValidatorSpec extends Specification {

  private static final ACCOUNT_NAME = "auto"

  @Shared
  CloudFoundryDeployDescriptionValidator validator

  @Shared
  CloudFoundryAccountCredentials cloudFoundryCredentials = TestCredential.named(ACCOUNT_NAME)

  void setupSpec() {
    validator = new CloudFoundryDeployDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentialsRepo.save(ACCOUNT_NAME, cloudFoundryCredentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  // TODO Reinstate test cases when validator is back online

  @Ignore('Reinstate test cases when validator is back online')
  void "pass validation with proper description inputs"() {
    setup:
    def description = new CloudFoundryDeployDescription(application: "foo", artifact: "foo.jar", api: "https://api.cf.com",
        org: "FrameworksAndRuntimes", space: "development", credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  @Ignore('Reinstate test cases when validator is back online')
  void "null input fails validation"() {
    setup:
    def description = new CloudFoundryDeployDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('api', _)
    1 * errors.rejectValue('org', _)
    1 * errors.rejectValue('space', _)
    1 * errors.rejectValue('application', _)
    1 * errors.rejectValue('artifact', _)
    1 * errors.rejectValue('credentials', _)
  }

  @Ignore('Reinstate test cases when validator is back online')
  void "invalid instances fails validation"() {
    setup:
    def description = new CloudFoundryDeployDescription(application: "foo", artifact: "foo.jar", api: "https://api.cf.com",
        org: "FrameworksAndRuntimes", space: "development", credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    description.instances = 0
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('instances', _, '0')

    when:
    description.instances = -1
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('instances', _, '-1')

    when:
    description.instances = "foo"

    then:
    thrown(GroovyCastException)

  }

  @Ignore('Reinstate test cases when validator is back online')
  void "valid instances pass validation"() {
    setup: "setup basic CF deploy description"
    def description = new CloudFoundryDeployDescription(application: "foo", artifact: "foo.jar", api: "https://api.cf.com",
        org: "FrameworksAndRuntimes", space: "development", credentials: cloudFoundryCredentials)
    def errors = Mock(Errors)

    when:
    description.instances = 1
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)

    when:
    description.instances = null
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

}
