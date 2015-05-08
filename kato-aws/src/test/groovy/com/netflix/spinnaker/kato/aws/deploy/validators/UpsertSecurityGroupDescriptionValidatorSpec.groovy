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

import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription.SecurityGroupIngress
import com.netflix.spinnaker.kato.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.aws.services.SecurityGroupService
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class UpsertSecurityGroupDescriptionValidatorSpec extends Specification {

  @Subject validator = new UpsertSecurityGroupDescriptionValidator()

  @Shared
  SecurityGroupService securityGroupService

  @Shared
  Errors errors

  def description = new UpsertSecurityGroupDescription().with {
    credentials = TestCredential.named('test')
    name = "foo"
    description = "desc"
    securityGroupIngress = [
      new SecurityGroupIngress().with {
        name = "bar"
        startPort = 111
        endPort = 111
        type = UpsertSecurityGroupDescription.IngressType.tcp
        it
      }
    ]
    it
  }

  def setup() {
    securityGroupService = Mock(SecurityGroupService)
    errors = Mock(Errors)
    def regionScopedProviderFactory = Mock(RegionScopedProviderFactory)
    def regionScopedProvider = Mock(RegionScopedProviderFactory.RegionScopedProvider)
    regionScopedProvider.getSecurityGroupService() >> securityGroupService
    regionScopedProviderFactory.forRegion(_, _) >> regionScopedProvider
    validator.regionScopedProviderFactory = regionScopedProviderFactory
  }

  void "should reject ingress unknown security groups when no prior security group create descriptions are found"() {
    when:
    validator.validate(_, description, errors)

    then:
    1 * securityGroupService.getSecurityGroupIds(_) >> { throw new SecurityGroupNotFoundException(missingSecurityGroups: ["sg-123"]) }
    1 * errors.rejectValue("securityGroupIngress", _, _)
  }

  void "should allow ingress from unknown security groups if they are intending to be created earlier in the chain"() {
    setup:
    def priorDesc = new UpsertSecurityGroupDescription(name: "foo")
    description.region = "us-west-1"
    description.securityGroupIngress = [new SecurityGroupIngress(name: "foo", startPort: 1, endPort: 1)]

    when:
    validator.validate([priorDesc], description, errors)

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
