/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.kato.gce.deploy.validators

import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployDescriptionValidatorSpec extends Specification {
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final FREE_FORM_DETAILS = "detail"
  private static final INITIAL_NUM_REPLICAS = 3
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final DISK_TYPE = "pd-standard"
  private static final DISK_SIZE_GB = 10
  private static final ZONE = "us-central1-b"
  private static final TAGS = ["some-tag-1", "some-tag-2", "some-tag-3"]
  private static final ACCOUNT_NAME = "auto"

  @Shared
  BasicGoogleDeployDescriptionValidator validator

  void setupSpec() {
    validator = new BasicGoogleDeployDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = Mock(GoogleNamedAccountCredentials)
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> new GoogleCredentials()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new BasicGoogleDeployDescription(application: APPLICATION,
                                                       stack: STACK,
                                                       initialNumReplicas: INITIAL_NUM_REPLICAS,
                                                       image: IMAGE,
                                                       instanceType: INSTANCE_TYPE,
                                                       diskType: DISK_TYPE,
                                                       diskSizeGb: DISK_SIZE_GB,
                                                       zone: ZONE,
                                                       accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with proper description inputs and free-form details"() {
    setup:
    def description = new BasicGoogleDeployDescription(application: APPLICATION,
                                                       stack: STACK,
                                                       freeFormDetails: FREE_FORM_DETAILS,
                                                       initialNumReplicas: INITIAL_NUM_REPLICAS,
                                                       image: IMAGE,
                                                       instanceType: INSTANCE_TYPE,
                                                       zone: ZONE,
                                                       accountName: ACCOUNT_NAME)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "pass validation with proper description inputs and tags"() {
    setup:
    def description = new BasicGoogleDeployDescription(application: APPLICATION,
      stack: STACK,
      freeFormDetails: FREE_FORM_DETAILS,
      initialNumReplicas: INITIAL_NUM_REPLICAS,
      image: IMAGE,
      instanceType: INSTANCE_TYPE,
      zone: ZONE,
      tags: TAGS,
      accountName: ACCOUNT_NAME)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "invalid initialNumReplicas fails validation"() {
    setup:
    def description = new BasicGoogleDeployDescription(initialNumReplicas: -1)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("initialNumReplicas", "basicGoogleDeployDescription.initialNumReplicas.negative")
  }

  void "invalid diskSizeGb fails validation"() {
    setup:
    def errors = Mock(Errors)

    when:
    validator.validate([], new BasicGoogleDeployDescription(diskSizeGb: -1), errors)

    then:
    1 * errors.rejectValue("diskSizeGb", "basicGoogleDeployDescription.diskSizeGb.invalid")

    when:
    validator.validate([], new BasicGoogleDeployDescription(diskSizeGb: 0), errors)

    then:
    1 * errors.rejectValue("diskSizeGb", "basicGoogleDeployDescription.diskSizeGb.invalid")

    when:
    validator.validate([], new BasicGoogleDeployDescription(diskSizeGb: 9), errors)

    then:
    1 * errors.rejectValue("diskSizeGb", "basicGoogleDeployDescription.diskSizeGb.invalid")
  }

  void "null input fails validation"() {
    setup:
      def description = new BasicGoogleDeployDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('application', _)
      1 * errors.rejectValue('image', _)
      1 * errors.rejectValue('instanceType', _)
      1 * errors.rejectValue('zone', _)
  }
}
