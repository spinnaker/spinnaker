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
import com.netflix.spinnaker.clouddriver.titus.TitusOperation
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@TitusOperation(AtomicOperations.CREATE_SERVER_GROUP)
class TitusDeployDescriptionValidator extends AbstractTitusDescriptionValidatorSupport<TitusDeployDescription> {

  @Autowired
  TitusDeployDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "titusDeployDescription")
  }

  @Override
  void validate(List priorDescriptions, TitusDeployDescription description, Errors errors) {

    super.validate(priorDescriptions, description, errors)

    if (!description.region) {
      errors.rejectValue "region", "titusDeployDescription.region.empty"
    }

    def credentials = getAccountCredentials(description?.credentials?.name)
    if (credentials && !((NetflixTitusCredentials) credentials).regions.name.contains(description.region)) {
      errors.rejectValue "region", "titusDeployDescription.region.not.configured", description.region, "Region not configured"
    }

    if (!description.application) {
      errors.rejectValue "application", "titusDeployDescription.application.empty"
    }

    if (!description.imageId) {
      errors.rejectValue "imageId", "titusDeployDescription.imageId.empty"
    }

    if (!description.capacity || description.capacity.desired <= 0) {
      errors.rejectValue "capacity", "titusDeployDescription.capacity.desired.invalid"
    }

    if (description.resources) {
      if (description.resources.cpu <= 0) {
        errors.rejectValue "resources.cpu", "titusDeployDescription.resources.cpu.invalid"
      }

      if (description.resources.memory <= 0) {
        errors.rejectValue "resources.memory", "titusDeployDescription.resources.memory.invalid"
      }

      if (description.resources.disk <= 0) {
        errors.rejectValue "resources.disk", "titusDeployDescription.resources.disk.invalid"
      }

      if (description.resources.ports) {
        description.resources.ports.each {
          if (it <= 0) {
            errors.rejectValue "resources.port", "titusDeployDescription.resources.port.invalid", it, "Invalid port specified"
          }
        }
      }
    } else {
      errors.rejectValue "resources", "titusDeployDescription.resources.empty"
    }

  }

}
import org.springframework.validation.Errors
