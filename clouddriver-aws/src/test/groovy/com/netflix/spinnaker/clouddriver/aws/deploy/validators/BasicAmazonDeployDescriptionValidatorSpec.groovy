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
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BasicAmazonDeployDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"

  @Shared
  BasicAmazonDeployDescriptionValidator validator

  @Shared
  NetflixAmazonCredentials amazonCredentials = TestCredential.named(ACCOUNT_NAME)

  @Shared
  def credentialsRepository = Stub(CredentialsRepository) {
    getOne("auto") >> {amazonCredentials}
  }
  void setupSpec() {
    validator = new BasicAmazonDeployDescriptionValidator()
    validator.credentialsRepository = credentialsRepository
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

  @Unroll
  void "valid request with launch template only feature #ltOnlyProperty and launch template enabled succeeds validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: true, instanceType: instanceType,
            application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    description."${ltOnlyProperty}" = propertyEnabled
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._

    where:
    ltOnlyProperty          | propertyEnabled  | instanceType
    'unlimitedCpuCredits'   | true             | 't3.large'
    'unlimitedCpuCredits'   | false            | 't3.large'
    'requireIMDSv2'         | true             | 'c3.small'
    'associateIPv6Address'  | true             | 'm5.large'
  }

  void "valid request with launch template disabled and all launch template only features omitted succeeds validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: false, instanceType: "c3.large",
            application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "valid request with launch template enabled and all launch template only features omitted succeeds validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: true, instanceType: instanceType,
            application: "foo", amiName: "foo",credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._

    where:
    instanceType << ['t2.large', 'c3.small']
  }

  void "valid request with unlimited cpu credits enabled succeeds validation for supported instance types"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: true, unlimitedCpuCredits: true, instanceType: instanceType,
            application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._

    where:
    instanceType << ['t2.large', 't3.small', 't3a.micro']
  }

  void "valid request with unlimited cpu credits disabled succeeds validation for supported instance types"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: true, unlimitedCpuCredits: false, instanceType: instanceType,
            application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._

    where:
    instanceType << ['t2.large', 't3.small', 't3a.micro']
  }

  void "request with launch template disabled but launch template only features enabled, ignores related values and succeeds validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            instanceType: instanceType, application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])

    and:
    description.setLaunchTemplate = false
    description."${ltOnlyProperty}" = propertyEnabled
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._

    where:
    ltOnlyProperty            | propertyEnabled   | instanceType
    'unlimitedCpuCredits'     | true              |  't3.large'
    'unlimitedCpuCredits'     | false             |  't3.large'
    'requireIMDSv2'           | true              |  'c3.small'
    'associateIPv6Address'    | true              |  'm5.large'
  }

  void "request with launch template disabled but launch template only features enabled generates warnings correctly"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            instanceType: "t3.large", application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])

    and:
    description.setLaunchTemplate = false
    description.requireIMDSv2 = requireIMDSv2
    description.associateIPv6Address = associateIPv6Address
    description.unlimitedCpuCredits = unlimitedCpuCredits

    when:
    def actualWarning = validator.getWarnings(description)

    then:
    final String expectedWarning = "WARNING: The following fields ${expectedFieldsInWarning} work as expected only with AWS EC2 Launch Template, " +
                    "but 'setLaunchTemplate' is set to false in request with account: ${description.account}, " +
                    "application: ${description.application}, stack: ${description.stack})"
    expectedWarning == actualWarning

    where:
    requireIMDSv2 | associateIPv6Address | unlimitedCpuCredits  | expectedFieldsInWarning
    false         |      false           |        true          | ["unlimitedCpuCredits"]
    false         |      false           |       false          | ["unlimitedCpuCredits"]
    true          |      false           |        null          | ["requireIMDSv2"]
    false         |      true            |        null          | ["associateIPv6Address"]
    true          |      false           |        true          | ["requireIMDSv2, unlimitedCpuCredits"]
    false         |      true            |       false          | ["associateIPv6Address, unlimitedCpuCredits"]
    true          |      true            |        null          | ["requireIMDSv2, associateIPv6Address"]
    true          |      true            |        true          | ["requireIMDSv2, associateIPv6Address, unlimitedCpuCredits"]
    true          |      true            |       false          | ["requireIMDSv2, associateIPv6Address, unlimitedCpuCredits"]
  }

  void "invalid request with unlimited cpu credits and unsupported instance type fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: true, unlimitedCpuCredits: true, instanceType: instanceType,
            application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("unlimitedCpuCredits", rejection)

    where:
    instanceType    | rejection
    'c3.large'      | 'basicAmazonDeployDescription.bursting.not.supported.by.instanceType'
    'm5.xlarge'     | 'basicAmazonDeployDescription.bursting.not.supported.by.instanceType'
    'r5.small'      | 'basicAmazonDeployDescription.bursting.not.supported.by.instanceType'
  }

  void "invalid request with standard (non-unlimited) cpu credits and unsupported / invalid instance type fails validation"() {
    setup:
    def description = new BasicAmazonDeployDescription(
            setLaunchTemplate: true, unlimitedCpuCredits: false, instanceType: instanceType,
            application: "foo", amiName: "foo", credentials: amazonCredentials, subnetType: "private-subnet",
            availabilityZones: ["us-east-1": ["us-east-1a", "us-east-1b", "us-east-1c"]], capacity: [min: 1, max: 1, desired: 1])
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("unlimitedCpuCredits", rejection)

    where:
    instanceType    | rejection
    'c3.large'      | 'basicAmazonDeployDescription.bursting.not.supported.by.instanceType'
    'm5.xlarge'     | 'basicAmazonDeployDescription.bursting.not.supported.by.instanceType'
    'r5.small'      | 'basicAmazonDeployDescription.bursting.not.supported.by.instanceType'
  }
}
