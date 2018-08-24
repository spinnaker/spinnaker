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

import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.deploy.description.ModifyGoogleServerGroupInstanceTemplateDescription
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class ModifyGoogleServerGroupInstanceTemplateDescriptionValidatorSpec extends Specification {
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final INSTANCE_METADATA = [
    "startup-script": "sudo apt-get update",
    "some-key": "some-value"
  ]
  private static final TAGS = ["some-tag-1", "some-tag-2"]
  private static final NETWORK = "default"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ModifyGoogleServerGroupInstanceTemplateDescriptionValidator validator

  void setupSpec() {
    def googleDeployDefaults = new GoogleConfiguration.DeployDefaults()
    validator = new ModifyGoogleServerGroupInstanceTemplateDescriptionValidator(googleDeployDefaults: googleDeployDefaults)
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with minimum proper description inputs"() {
    setup:
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with all optional inputs"() {
    setup:
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                               region: REGION,
                                                                               image: IMAGE,
                                                                               instanceType: INSTANCE_TYPE,
                                                                               instanceMetadata: INSTANCE_METADATA,
                                                                               tags: TAGS,
                                                                               network: NETWORK,
                                                                               accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new ModifyGoogleServerGroupInstanceTemplateDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      // The point of being explicit here is only to verify the context and property names.
      // We'll assume the policies are covered by the tests on the underlying validator.
      1 * errors.rejectValue('serverGroupName', "modifyGoogleServerGroupInstanceTemplateDescription.serverGroupName.empty")
      1 * errors.rejectValue('region', "modifyGoogleServerGroupInstanceTemplateDescription.region.empty")
      1 * errors.rejectValue('credentials', "modifyGoogleServerGroupInstanceTemplateDescription.credentials.empty")
  }
}
