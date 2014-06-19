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

import com.netflix.spinnaker.kato.deploy.aws.AutoScalingWorker.SubnetType
import com.netflix.spinnaker.kato.deploy.aws.AutoScalingWorker.SubnetTypeNotFoundException
import com.netflix.spinnaker.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("createAmazonLoadBalancerDescriptionValidator")
class CreateAmazonLoadBalancerDescriptionValidator extends AmazonDescriptionValidationSupport<CreateAmazonLoadBalancerDescription> {
  @Override
  void validate(List priorDescriptions, CreateAmazonLoadBalancerDescription description, Errors errors) {
    if (!description.clusterName) {
      errors.rejectValue("clusterName", "createAmazonLoadBalancerDescription.clusterName.empty")
    }
    if (!description.subnetType && !description.availabilityZones) {
      errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.missing.subnetType.or.availabilityZones")
    }
    for (Map.Entry entry in description.availabilityZones) {
      def region = entry.key
      def azs = entry.value
      def acct = awsConfigurationProperties.accounts.find { it.name == description.credentialAccount }
      def acctRegion = acct ? acct.regions.find { it.name == region } : null

      if (!awsConfigurationProperties.regions.contains(region)) {
        errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.region.not.configured")
      }
      if (!description.subnetType && !azs) {
        errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.missing.subnetType.or.availabilityZones")
        break
      }
      if (!description.subnetType && acctRegion && !acctRegion.availabilityZones.containsAll(azs)) {
        errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.zone.not.configured")
      }
    }
    if (description.subnetType) {
      try {
        SubnetType.fromString(description.subnetType)
      } catch (SubnetTypeNotFoundException e) {
        errors.rejectValue("subnetType", "createAmazonLoadBalancerDescription.subnetType.not.configured")
      }
    }
    if (!description.listeners) {
      errors.rejectValue("listeners", "createAmazonLoadBalancerDescription.listeners.empty")
    }
  }
}
