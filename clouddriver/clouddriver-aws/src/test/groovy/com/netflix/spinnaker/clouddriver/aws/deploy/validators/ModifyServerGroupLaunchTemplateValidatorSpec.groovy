/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class ModifyServerGroupLaunchTemplateValidatorSpec extends Specification {

  private static final ACCOUNT_NAME = "auto"

  @Shared
  ModifyServerGroupLaunchTemplateValidator validator

  @Shared
  NetflixAmazonCredentials amazonCredentials = TestCredential.named(ACCOUNT_NAME)

  @Shared
  def credentialsRepository = Stub(CredentialsRepository) {
    getOne("auto") >> {amazonCredentials}
  }

  void setupSpec() {
    validator = new ModifyServerGroupLaunchTemplateValidator()
    validator.credentialsRepository = credentialsRepository
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      asgName: "my-asg-v000", region: "us-east-1", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, subnetType: "internal")
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "should fail validation if asgName is unset"() {
    setup:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      asgName: asgName, region: "us-east-1", amiName: "foo", instanceType: "foo", credentials: amazonCredentials, subnetType: "internal")
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("asgName", "modifyservergrouplaunchtemplatedescription.asgName.empty")

    where:
    asgName << [null, "", " "]
  }

  void "should fail validation if only metadata fields are set"() {
    setup:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      asgName: "my-asg-v000", region: "us-east-1", credentials: amazonCredentials)
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("multiple fields",
      "modifyservergrouplaunchtemplatedescription.launchTemplateAndServerGroupFields.empty",
      "No changes requested to launch template or related server group fields for modifyServerGroupLaunchTemplate operation.")
  }

  void "valid request with unlimited cpu credits succeeds validation for supported instance types"() {
    setup:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      asgName: "my-asg-v000", region: "us-east-1", amiName: "foo", instanceType: instanceType,
      credentials: amazonCredentials, subnetType: "internal", unlimitedCpuCredits: unlimitedCpuCredits)
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    if(expectedError == null) {
      0 * errors._
    } else {
      1 * errors.rejectValue("unlimitedCpuCredits", expectedError)
    }

    where:
    instanceType    |   unlimitedCpuCredits     ||  expectedError
    't2.large'      |   null                    ||  null
    't3.small'      |   true                    ||  null
    't3a.micro'     |   false                   ||  null
    'c3.large'      |   false                   || 'modifyservergrouplaunchtemplatedescription.bursting.not.supported.by.instanceType'
    'c3.large'      |   true                    || 'modifyservergrouplaunchtemplatedescription.bursting.not.supported.by.instanceType'
  }

  void "validate all instance types in a request with multiple instance types"() {
    setup:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      asgName: "my-asg-v000", amiName: "foo", region: "us-east-1", credentials: amazonCredentials, subnetType: "internal")
    def errors = Mock(ValidationErrors)

    and:
    ltOnlyPropertyAndValue.each { entry ->
      description."$entry.key" = entry.value
    }

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("unlimitedCpuCredits", rejection)

    where:
    ltOnlyPropertyAndValue                                                              || rejection
    [instanceType: "t2.large", unlimitedCpuCredits: true,
     launchTemplateOverridesForInstanceType:[
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
         instanceType: "m5.xlarge", weightedCapacity: 4)]]                              || 'modifyservergrouplaunchtemplatedescription.bursting.not.supported.by.instanceType'

    [instanceType: "c3.large", unlimitedCpuCredits: true,
     launchTemplateOverridesForInstanceType:[
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
         instanceType: "t2.xlarge", weightedCapacity: 2)]]                              || 'modifyservergrouplaunchtemplatedescription.bursting.not.supported.by.instanceType'

    [instanceType: "t3.small", unlimitedCpuCredits: true,
     spotAllocationStrategy: "lowest-price",
     launchTemplateOverridesForInstanceType:[
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
         instanceType: "c3.large", weightedCapacity: 4),
       new BasicAmazonDeployDescription.LaunchTemplateOverridesForInstanceType(
         instanceType: "t3.large", weightedCapacity: 2)]]                                  || 'modifyservergrouplaunchtemplatedescription.bursting.not.supported.by.instanceType'
  }

  void "invalid request with spotInstancePools and unsupported spotAllocationStrategy fails validation"() {
    setup:
    def description = new ModifyServerGroupLaunchTemplateDescription(
      asgName: "my-asg-v000", region: "us-east-1", amiName: "foo", credentials: amazonCredentials,
      subnetType: "internal", spotInstancePools: 3, spotAllocationStrategy: "capacity-optimized")
    def errors = Mock(ValidationErrors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("spotInstancePools", "modifyservergrouplaunchtemplatedescription.spotInstancePools.not.supported.for.spotAllocationStrategy")
  }
}
