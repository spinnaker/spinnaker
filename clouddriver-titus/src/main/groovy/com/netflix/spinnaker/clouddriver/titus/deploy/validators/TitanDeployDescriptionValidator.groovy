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

package com.netflix.spinnaker.clouddriver.titus.deploy.validators

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitanOperation
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitanDeployDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@TitanOperation(AtomicOperations.CREATE_SERVER_GROUP)
class TitanDeployDescriptionValidator extends AbstractTitanDescriptionValidatorSupport<TitanDeployDescription> {

  @Autowired
  TitanDeployDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "titanDeployDescription")
  }

  @Override
  void validate(List priorDescriptions, TitanDeployDescription description, Errors errors) {

    super.validate(priorDescriptions, description, errors)

    if (!description.region) {
      errors.rejectValue "region", "titanDeployDescription.region.empty"
    }

    def credentials = getAccountCredentials(description?.credentials?.name)
    if (credentials && !((NetflixTitanCredentials) credentials).regions.name.contains(description.region)) {
      errors.rejectValue "region", "titanDeployDescription.region.not.configured", description.region, "Region not configured"
    }

    if (!description.application) {
      errors.rejectValue "application", "titanDeployDescription.application.empty"
    }

    if (!description.imageId) {
      errors.rejectValue "imageId", "titanDeployDescription.imageId.empty"
    }

    if (!description.capacity || description.capacity.desired <= 0) {
      errors.rejectValue "capacity", "titanDeployDescription.capacity.desired.invalid"
    }

    if (description.resources) {
      if (description.resources.cpu <= 0) {
        errors.rejectValue "resources.cpu", "titanDeployDescription.resources.cpu.invalid"
      }

      if (description.resources.memory <= 0) {
        errors.rejectValue "resources.memory", "titanDeployDescription.resources.memory.invalid"
      }

      if (description.resources.disk <= 0) {
        errors.rejectValue "resources.disk", "titanDeployDescription.resources.disk.invalid"
      }

      if (description.resources.ports) {
        description.resources.ports.each {
          if (it <= 0) {
            errors.rejectValue "resources.port", "titanDeployDescription.resources.port.invalid", it, "Invalid port specified"
          }
        }
      }
    } else {
      errors.rejectValue "resources", "titanDeployDescription.resources.empty"
    }

  }

}
import org.springframework.validation.Errors
