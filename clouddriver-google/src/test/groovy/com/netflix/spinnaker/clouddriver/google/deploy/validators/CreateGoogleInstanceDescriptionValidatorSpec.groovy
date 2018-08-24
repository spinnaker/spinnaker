/*
 * Copyright 2014 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class CreateGoogleInstanceDescriptionValidatorSpec extends Specification {
  private static final INSTANCE_NAME = "my-app-v000"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final DISK_PD_SSD = new GoogleDisk(type: "pd-ssd", sizeGb: 125)
  private static final DISK_LOCAL_SSD = new GoogleDisk(type: "local-ssd", sizeGb: 375)
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  CreateGoogleInstanceDescriptionValidator validator

  void setupSpec() {
    def googleDeployDefaults = new GoogleConfiguration.DeployDefaults()
    validator = new CreateGoogleInstanceDescriptionValidator(googleDeployDefaults: googleDeployDefaults)
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new CreateGoogleInstanceDescription(instanceName: INSTANCE_NAME,
                                                            image: IMAGE,
                                                            instanceType: INSTANCE_TYPE,
                                                            disks: [DISK_PD_SSD, DISK_LOCAL_SSD],
                                                            zone: ZONE,
                                                            accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new CreateGoogleInstanceDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('instanceName', _)
      1 * errors.rejectValue('image', _)
      1 * errors.rejectValue('instanceType', _)
      1 * errors.rejectValue('zone', _)
  }
}
