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

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.aws.services.SecurityGroupService
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertSecurityGroupDescriptionValidatorSpec extends Specification {

  @Subject validator = new UpsertSecurityGroupDescriptionValidator()

  SecurityGroupService securityGroupService = Mock(SecurityGroupService)
  ValidationErrors errors = Mock(ValidationErrors)

  def description = new UpsertSecurityGroupDescription(
    credentials: TestCredential.named('test'),
    region: "us-east-1",
    name: "foo",
    description: "desc",
    securityGroupIngress: [
      new SecurityGroupIngress(
        name: "bar",
        startPort: 111,
        endPort: 111,
        ipProtocol: "tcp"
      )
    ])

  def setup() {
    securityGroupService = Mock(SecurityGroupService)
    errors = Mock(ValidationErrors)
    def regionScopedProviderFactory = Mock(RegionScopedProviderFactory)
    def regionScopedProvider = Mock(RegionScopedProviderFactory.RegionScopedProvider)
    regionScopedProvider.getSecurityGroupService() >> securityGroupService
    regionScopedProviderFactory.forRegion(_, _) >> regionScopedProvider
    validator.regionScopedProviderFactory = regionScopedProviderFactory
  }

  void "should reject ingress when unidentified"() {
    description.securityGroupIngress = [
      new SecurityGroupIngress(
        accountName: "bar"
      )
    ]

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("securityGroupIngress", "upsertSecurityGroupDescription.ingress.without.identifier",
    "Ingress for 'foo' was missing identifier: bar..")
  }

  void "should allow ingress with name"() {
    description.securityGroupIngress = [
      new SecurityGroupIngress(
        name: "bar"
      )
    ]

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void "should allow ingress with id"() {
    description.securityGroupIngress = [
      new SecurityGroupIngress(
        id: "bar"
      )
    ]

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  void "region is validates against configuration"() {
    setup:
    def description = getDescription()
    description.region = "us-east-5"

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    when:
    description.region = 'us-east-1'
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)
  }

}
