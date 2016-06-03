/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class TerminateAndDecrementGoogleServerGroupDescriptionValidatorSpec extends Specification {
  private static final SERVER_GROUP_NAME = "server-group-name"
  private static final ACCOUNT_NAME = "auto"
  private static final REGION = "us-central1"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-instance1", "my-app7-dev-v000-instance2"]

  @Shared
  TerminateAndDecrementGoogleServerGroupDescriptionValidator validator

  void setupSpec() {
    validator = new TerminateAndDecrementGoogleServerGroupDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new TerminateAndDecrementGoogleServerGroupDescription(
          region: REGION, serverGroupName: SERVER_GROUP_NAME, instanceIds: INSTANCE_IDS, accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "invalid instanceIds fail validation"() {
    setup:
      def description = new TerminateAndDecrementGoogleServerGroupDescription(instanceIds: [""], serverGroupName: SERVER_GROUP_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue("instanceIds", "terminateAndDecrementGoogleServerGroupDescription.instanceId0.empty")
  }

  void "null input fails validation"() {
    setup:
      def description = new TerminateAndDecrementGoogleServerGroupDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('region', _)
      1 * errors.rejectValue('serverGroupName', _)
      1 * errors.rejectValue('instanceIds', _)
  }
}
