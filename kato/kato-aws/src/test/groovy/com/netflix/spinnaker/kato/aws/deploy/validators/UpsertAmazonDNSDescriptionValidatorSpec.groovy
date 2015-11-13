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

import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesResult
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonDNSDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertAmazonDNSDescriptionValidatorSpec extends Specification {

  @Shared
  UpsertAmazonDNSDescriptionValidator validator = new UpsertAmazonDNSDescriptionValidator()

  void "empty description fails validation"() {
    setup:
    def description = new UpsertAmazonDNSDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("type", _)
    1 * errors.rejectValue("name", _)
    1 * errors.rejectValue("target", _)
    1 * errors.rejectValue("hostedZoneName", _)
  }

  void "type validates against prescribed list"() {
    setup:
    def description = new UpsertAmazonDNSDescription()
    description.target = "foo"
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("type", _)

    when:
    description.target = "CNAME"

    then:
    0 * errors.rejectValue("type", _)
  }

  void "empty target is allowed when an upstream load balancer is produced"() {
    setup:
    def elbDescription = new UpsertAmazonLoadBalancerDescription()
    def description = new UpsertAmazonDNSDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([elbDescription], description, errors)

    then:
    0 * errors.rejectValue("target", _)
  }

  void "hostedZone is resolved for validation"() {
    setup:
    def description = new UpsertAmazonDNSDescription()
    description.type = "CNAME"
    description.target = "foo.netflix.net."
    description.hostedZoneName = "netflix.net."
    def errors = Mock(Errors)
    def route53 = Mock(AmazonRoute53)
    validator.amazonClientProvider = Mock(AmazonClientProvider)
    validator.amazonClientProvider.getAmazonRoute53(_, _, true) >> route53

    when:
    validator.validate([], description, errors)

    then:
    1 * route53.listHostedZones() >> {
      def zone = Mock(HostedZone)
      zone.getName() >> "netflix.net."
      new ListHostedZonesResult().withHostedZones(zone)
    }
  }
}
