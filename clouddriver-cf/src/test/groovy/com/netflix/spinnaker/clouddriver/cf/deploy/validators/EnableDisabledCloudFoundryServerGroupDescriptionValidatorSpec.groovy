/*
 * Copyright 2015 Pivotal Inc.
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

import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.deploy.description.EnableDisableCloudFoundryServerGroupDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests to verify Enable/Disable Server Group validator.
 *
 * NOTE: Since both validators are simply declarative subclasses on one abstract validator,
 * both sets of tests are handled in this one test spec.
 *
 */
class EnableDisabledCloudFoundryServerGroupDescriptionValidatorSpec extends Specification {

  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final REGION = "some-region"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  EnableCloudFoundryServerGroupDescriptionValidator enableValidator

  @Shared
  DisableCloudFoundryServerGroupDescriptionValidator disableValidator

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentialsRepo.save(ACCOUNT_NAME, TestCredential.named(ACCOUNT_NAME))

    enableValidator = new EnableCloudFoundryServerGroupDescriptionValidator(
        accountCredentialsProvider: credentialsProvider)
    disableValidator = new DisableCloudFoundryServerGroupDescriptionValidator(
        accountCredentialsProvider: credentialsProvider)
  }

  void "should validate enableServerGroup with proper inputs"() {
    setup:
      def description = new EnableDisableCloudFoundryServerGroupDescription(
          serverGroupName: SERVER_GROUP_NAME,
          region: REGION,
          credentials: TestCredential.named(ACCOUNT_NAME))
      def errors = Mock(Errors)

    when:
      enableValidator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails enableServerGroup validation"() {
    setup:
      def description = new EnableDisableCloudFoundryServerGroupDescription()
      def errors = Mock(Errors)

    when:
      enableValidator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('serverGroupName', _)
      1 * errors.rejectValue('region', _)
  }

  void "should validate disableServerGroup with proper inputs"() {
    setup:
    def description = new EnableDisableCloudFoundryServerGroupDescription(
        serverGroupName: SERVER_GROUP_NAME,
        region: REGION,
        credentials: TestCredential.named(ACCOUNT_NAME))
    def errors = Mock(Errors)

    when:
    disableValidator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "null input fails disableServerGroup validation"() {
    setup:
    def description = new EnableDisableCloudFoundryServerGroupDescription()
    def errors = Mock(Errors)

    when:
    disableValidator.validate([], description, errors)

    then:
    1 * errors.rejectValue('credentials', _)
    1 * errors.rejectValue('serverGroupName', _)
    1 * errors.rejectValue('region', _)
  }
}
