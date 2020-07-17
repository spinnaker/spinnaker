/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.DcosOperation
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@DcosOperation(AtomicOperations.CREATE_SERVER_GROUP)
class DeployDcosServerGroupDescriptionValidator extends AbstractDcosDescriptionValidatorSupport<DeployDcosServerGroupDescription> {

  @Autowired
  DeployDcosServerGroupDescriptionValidator(AccountCredentialsProvider accountCredentialsProvider) {
    super(accountCredentialsProvider, "deployDcosServerGroupDescription")
  }

  @Override
  void validate(List priorDescriptions, DeployDcosServerGroupDescription description, ValidationErrors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.region || description.region.empty) {
      errors.rejectValue "region", "${descriptionName}.region.empty"
    } else if (!isRegionValid(description.region)) {
      errors.rejectValue "region", "${descriptionName}.region.invalid"
    }

    if (!description.application) {
      errors.rejectValue "application", "${descriptionName}.application.empty"
    } else if (!MarathonPathId.isPartValid(description.application)) {
      errors.rejectValue "application", "${descriptionName}.application.invalid"
    }

    if (description.stack && !MarathonPathId.isPartValid(description.stack)) {
      errors.rejectValue "stack", "${descriptionName}.stack.invalid"
    }

    if (description.freeFormDetails && !MarathonPathId.isPartValid(description.freeFormDetails)) {
      errors.rejectValue "freeFormDetails", "${descriptionName}.freeFormDetails.invalid"
    }

    if (!description.desiredCapacity || description.desiredCapacity <= 0) {
      errors.rejectValue "desiredCapacity", "${descriptionName}.desiredCapacity.invalid"
    }

    if (!description.cpus || description.cpus <= 0) {
      errors.rejectValue "cpus", "${descriptionName}.cpus.invalid"
    }

    if (!description.mem || description.mem <= 0) {
      errors.rejectValue "mem", "${descriptionName}.mem.invalid"
    }

    if (description.disk && description.disk < 0) {
      errors.rejectValue "disk", "${descriptionName}.disk.invalid"
    }

    if (description.gpus && description.gpus < 0) {
      errors.rejectValue "gpus", "${descriptionName}.gpus.invalid"
    }
  }

  private static boolean isRegionValid(final String region) {
    def regionParts = region.replaceAll(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR)
      .split(MarathonPathId.PART_SEPARATOR).toList()

    for (part in regionParts) {
      if (!MarathonPathId.isPartValid(part)) {
        return false
      }
    }

    regionParts.size() > 0
  }
}
