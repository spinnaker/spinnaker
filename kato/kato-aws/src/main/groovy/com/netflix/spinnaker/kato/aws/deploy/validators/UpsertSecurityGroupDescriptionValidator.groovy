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

import com.netflix.spinnaker.kato.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.kato.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component("upsertSecurityGroupDescriptionValidator")
class UpsertSecurityGroupDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertSecurityGroupDescription> {
  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Override
  void validate(List priorDescriptions, UpsertSecurityGroupDescription description, Errors errors) {
    if (description.securityGroupIngress) {
      def securityGroups = description.securityGroupIngress.collect { it.name }
      def securityGroupService = regionScopedProviderFactory.forRegion(description.credentials, description.region).securityGroupService
      try {
        if (description.vpcId) {
          securityGroupService.getSecurityGroupIds(securityGroups, description.vpcId)
        } else {
          securityGroupService.getSecurityGroupIds(securityGroups)
        }
      } catch (SecurityGroupNotFoundException ex) {
        def priorSecurityGroupCreateDescriptions = (List<UpsertSecurityGroupDescription>) priorDescriptions.findAll { it instanceof UpsertSecurityGroupDescription }
        if (ex.missingSecurityGroups && !priorSecurityGroupCreateDescriptions*.name.containsAll(ex.missingSecurityGroups)) {
          errors.rejectValue(
            "securityGroupIngress",
            "upsertSecurityGroupDescription.security.group.not.found",
            "The following security groups do not exist: ${ex.missingSecurityGroups.join(", ")} (account: ${description.credentials.accountId}, region: ${description.region}, vpcId: ${description.vpcId})"
          )
        }
      }
    }

    validateRegions(description, [description.region], "upsertSecurityGroupDescription", errors)
  }
}
