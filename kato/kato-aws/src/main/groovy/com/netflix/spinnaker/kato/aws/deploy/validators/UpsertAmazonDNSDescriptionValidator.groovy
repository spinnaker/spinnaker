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

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonDNSDescription
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("upsertAmazonDNSDescriptionValidator")
class UpsertAmazonDNSDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertAmazonDNSDescription> {
  private static final List<String> ALLOWED_TYPES = ['A', 'CNAME', 'PTR', 'AAAA']

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  void validate(List priorDescriptions, UpsertAmazonDNSDescription description, Errors errors) {
    def upstreamElb = priorDescriptions.find { it instanceof UpsertAmazonLoadBalancerDescription }
    if (!upstreamElb && !description.target) {
      errors.rejectValue("target", "upsertAmazonDNSDescription.target.empty")
    }
    if (!description.name) {
      errors.rejectValue("name", "upsertAmazonDNSDescription.name.empty")
    }
    if (!description.type) {
      errors.rejectValue("type", "upsertAmazonDNSDescription.type.empty")
    } else if (!ALLOWED_TYPES.contains(description.type)) {
      errors.rejectValue("type", "upsertAmazonDNSDescription.type.invalid")
    } else if (ALLOWED_TYPES.contains(description.type) && description.target && description.hostedZoneName) {
      def route53 = amazonClientProvider.getAmazonRoute53(description.credentials, null, true)
      def allowedNames = route53.listHostedZones().hostedZones.collect {
        it.name
      }
      if (!allowedNames.contains(description.hostedZoneName)) {
        errors.rejectValue("hostedZoneName", "upsertAmazonDNSDescription.hostedZoneName.invalid")
      }
    }
    if (!description.hostedZoneName) {
      errors.rejectValue("hostedZoneName", "upsertAmazonDNSDescription.hostedZoneName.empty")
    }
  }
}
