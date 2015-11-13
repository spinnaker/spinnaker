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

package com.netflix.spinnaker.kato.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertAmazonLoadBalancerDescriptionValidatorSpec extends Specification {

  @Shared
  CreateAmazonLoadBalancerDescriptionValidator validator

  UpsertAmazonLoadBalancerDescription description

  void setupSpec() {
    validator = new CreateAmazonLoadBalancerDescriptionValidator()
  }

  void setup() {
    description = new UpsertAmazonLoadBalancerDescription(credentials:
            new NetflixAmazonCredentials('test', 'test', 'test', '12345', 'kp', [new AmazonCredentials.AWSRegion("us-west-1", ["us-west-1a"])], null, null, null, null, null, null, null, null, null))
  }

  void "empty parameters fails validation"() {
    setup:
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("clusterName", _)
    1 * errors.rejectValue("availabilityZones", _)
    1 * errors.rejectValue("listeners", _)
  }

  void "unconfigured region is rejected"() {
    setup:
    description.availabilityZones = ["us-west-5": ["us-west-5a"]]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _)
  }

  void "availability zone not configured for account is rejected"() {
    setup:
    description.availabilityZones = ["us-west-1": ["us-west-1b"]]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _)

  }

  void "subnetType supercedes availabilityZones"() {
    setup:
    description.subnetType = "internal"
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("availabilityZones", _)
  }

  void "availabilityZones if not subnetType"() {
    setup:
    description.availabilityZones = ["us-west-1": ["us-west-1a"]]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("subnetType", _)
    0 * errors.rejectValue("availabilityZones", _)
  }

}
