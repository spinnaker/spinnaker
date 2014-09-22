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


package com.netflix.spinnaker.kato.deploy.gce.validators

import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.amos.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kato.deploy.gce.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials
import com.netflix.spinnaker.kato.security.gce.GoogleNamedAccountCredentials
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployDescriptionValidatorSpec extends Specification {
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
      def description = new BasicGoogleDeployDescription(application: "asgard", stack: "asgard-test",
                                                         image: "debian-7-wheezy-v20140415", type: "f1-micro",
                                                         zone: "us-central1-b", accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
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
      1 * errors.rejectValue('stack', _)
      1 * errors.rejectValue('image', _)
      1 * errors.rejectValue('type', _)
      1 * errors.rejectValue('zone', _)
  }
}
