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


package com.netflix.spinnaker.kato.deploy.aws.validators

import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import com.netflix.spinnaker.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.security.DefaultNamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.security.aws.AmazonRoleAccountCredentials
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"

  @Shared
  BasicAmazonDeployDescriptionValidator validator

  @Shared
  AmazonCredentials amazonCredentials = new AmazonCredentials(null, ACCOUNT_NAME)

  void setupSpec() {
    validator = new BasicAmazonDeployDescriptionValidator(awsConfigurationProperties: new KatoAWSConfig.AwsConfigurationProperties(regions: ["us-west-1", "us-west-2"]))
    def credentialsHolder = new DefaultNamedAccountCredentialsHolder()
    def credentials = Mock(AmazonRoleAccountCredentials)
    credentials.getRegions() >> ["us-west-1"]
    credentialsHolder.put(ACCOUNT_NAME, credentials)
    validator.namedAccountCredentialsHolder = credentialsHolder
  }

  void "null input fails valiidation"() {
    setup:
    def description = new BasicAmazonDeployDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('availabilityZones', _)
    1 * errors.rejectValue('availabilityZones', _)
    1 * errors.rejectValue('instanceType', _)
    1 * errors.rejectValue('amiName', _)
    1 * errors.rejectValue('application', _)
    1 * errors.rejectValue('credentials', _)
  }

  void "invalid capacity fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-west-1": []])
    description.capacity.min = 5
    description.capacity.max = 3
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', _, ['5', '3', '0'], _)

    when:
    description.capacity.min = 3
    description.capacity.max = 5
    description.capacity.desired = 7
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', _, ['3', '5', '7'], _)
  }

  void "unconfigured region fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["eu-west-5": []])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _, ["eu-west-5"], _)
  }

  void "unconfigured account region fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-west-2": []])
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _, ["us-west-2"], _)
  }
}
