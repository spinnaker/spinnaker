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

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.model.SecurityGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component("upsertSecurityGroupDescriptionValidator")
class UpsertSecurityGroupDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertSecurityGroupDescription> {
  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Override
  void validate(List priorDescriptions, UpsertSecurityGroupDescription description, ValidationErrors errors) {
    if (!description.name) {
      errors.rejectValue("name", "upsertSecurityGroupDescription.name.not.nullable")
    }
    if (!description.description && !description.ingressAppendOnly) {
      errors.rejectValue("description", "upsertSecurityGroupDescription.description.not.nullable")
    }

    if (description.securityGroupIngress.find{ it.id == null && it.name == null }) {
      errors.rejectValue(
        "securityGroupIngress",
        "upsertSecurityGroupDescription.ingress.without.identifier",
        "Ingress for '$description.name' was missing identifier: ${description.securityGroupIngress.join(", ")}"
      )
    }
    validateRegions(description, [description.region], "upsertSecurityGroupDescription", errors)
  }

}
