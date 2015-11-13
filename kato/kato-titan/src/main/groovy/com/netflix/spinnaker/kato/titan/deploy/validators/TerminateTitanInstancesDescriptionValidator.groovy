/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.titan.deploy.validators

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titan.TitanOperation
import com.netflix.spinnaker.clouddriver.titan.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.titan.deploy.description.TerminateTitanInstancesDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@Component
@TitanOperation(AtomicOperations.TERMINATE_INSTANCES)
class TerminateTitanInstancesDescriptionValidator extends AbstractTitanDescriptionValidatorSupport<TerminateTitanInstancesDescription> {

  @Autowired
  TerminateTitanInstancesDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "terminateTitanInstancesDescription")
  }

  @Override
  void validate(List priorDescriptions, TerminateTitanInstancesDescription description, Errors errors) {

    super.validate(priorDescriptions, description, errors)

    if (!description.region) {
      errors.rejectValue "region", "terminateTitanInstancesDescription.region.empty"
    }

    def credentials = getAccountCredentials(description?.credentials?.name)
    if (credentials && !((NetflixTitanCredentials) credentials).regions.name.contains(description.region)) {
      errors.rejectValue "region", "terminateTitanInstancesDescription.region.not.configured", description.region, "Region not configured"
    }

    if (description.instanceIds) {
      description.instanceIds.each {
        if (!it) {
          errors.rejectValue "instanceId", "terminateTitanInstancesDescription.instanceId.empty"
        }
      }
    } else {
      errors.rejectValue "instanceIds", "terminateTitanInstancesDescription.instanceIds.empty"
    }
  }

}
