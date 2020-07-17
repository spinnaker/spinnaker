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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BasicAmazonDeployDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"

  @Shared
  BasicAmazonDeployDescriptionValidator validator

  @Shared
  NetflixAmazonCredentials amazonCredentials = TestCredential.named(ACCOUNT_NAME)

  void setupSpec() {
    validator = new BasicAmazonDeployDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentialsRepo.save(ACCOUNT_NAME, amazonCredentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-east-1": []],
      capacity: [min: 1, max: 1, desired: 1], subnetType: "internal")
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "should fail validation if public IP address without subnet"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-east-1": []],
      capacity: [min: 1, max: 1, desired: 1], associatePublicIpAddress: true)
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("associatePublicIpAddress", "basicAmazonDeployDescription.associatePublicIpAddress.subnetType.not.supplied")

    when:
    description.subnetType = "internal"
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "null input fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription()
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    2 * errors.rejectValue('availabilityZones', _)
    1 * errors.rejectValue('instanceType', _)
    1 * errors.rejectValue('amiName', _)
    1 * errors.rejectValue('application', _)
    1 * errors.rejectValue('credentials', _)
  }

  @Unroll
  void "min/max/desired = #min/#max/#desired should have #numExpectedErrors errors"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-east-1": []])
    description.capacity = [ min, max, desired ]
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    numExpectedErrors * errors.rejectValue('capacity', _, _, _)

    where:
    min  | max  | desired || numExpectedErrors
    5    | 3    | 4       || 2  // min > max, desired < min
    5    | 3    | null    || 1  // min > max, desired is irrelevant
    3    | 5    | 7       || 1  // desired > max
    null | 5    | 7       || 1  // desired > max, min is irrelevant
    3    | 5    | 0       || 1  // desired < min
    3    | null | 0       || 1  // desired < min, max is irrelevant
    3    | 7    | 5       || 0
    3    | 7    | 3       || 0
    3    | 7    | 7       || 0
    3    | 5    | null    || 0
    3    | null | 7       || 0
    null | 7    | 5       || 0
    null | null | 7       || 0
    null | null | null    || 0
  }

  void "unconfigured region fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["eu-west-5": []])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _, ["eu-west-5"], _)
  }

  void "unconfigured account region fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-west-2": []])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("availabilityZones", _, ["us-west-2"], _)
  }

  @Unroll
  void "invalid block device fails validation"(AmazonBlockDevice blockDevice, String rejection) {
    setup:
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-east-1": []],
      capacity: [min: 1, max: 1, desired: 1], subnetType: "internal", blockDevices: [blockDevice])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("blockDevices", rejection, _, _)

    where:
    blockDevice                                                                        | rejection
    new AmazonBlockDevice()                                                            | 'basicAmazonDeployDescription.block.device.not.named'
    new AmazonBlockDevice(deviceName: '/dev/sdb', virtualName: 'ephemeral0', size: 69) | 'basicAmazonDeployDescription.block.device.ephemeral.config'
    new AmazonBlockDevice(deviceName: '/dev/sdb', iops: 1)                             | 'basicAmazonDeployDescription.block.device.ebs.config'

  }

  void "valid block devices validate"() {
    setup:
    def blockDevices = [
      new AmazonBlockDevice(deviceName: '/dev/sdb', virtualName: 'ephemeral0'),
      new AmazonBlockDevice(deviceName: '/dev/sdb', size: 69)
    ]
    def description = new BasicAmazonDeployDescription(application: "foo", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, availabilityZones: ["us-east-1": []],
      capacity: [min: 1, max: 1, desired: 1], subnetType: "internal", blockDevices: blockDevices)
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }
}
