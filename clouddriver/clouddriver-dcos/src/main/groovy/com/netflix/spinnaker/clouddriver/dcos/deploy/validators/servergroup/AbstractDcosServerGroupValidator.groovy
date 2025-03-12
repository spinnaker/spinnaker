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

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.AbstractDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider

abstract class AbstractDcosServerGroupValidator<T extends AbstractDcosServerGroupDescription> extends AbstractDcosDescriptionValidatorSupport<T> {

  AbstractDcosServerGroupValidator(AccountCredentialsProvider accountCredentialsProvider, String descriptionName) {
    super(accountCredentialsProvider, descriptionName)
  }

  @Override
  void validate(List priorDescriptions, AbstractDcosServerGroupDescription description, ValidationErrors errors) {
    super.validate(priorDescriptions, description, errors)

    if (!description.region || description.region.empty) {
      errors.rejectValue "region", "${descriptionName}.region.empty"
    } else if (!isRegionValid(description.region)) {
      errors.rejectValue "region", "${descriptionName}.region.invalid"
    }

    if (!description.serverGroupName) {
      errors.rejectValue "serverGroupName", "${descriptionName}.serverGroupName.empty"
    } else if (!MarathonPathId.isPartValid(description.serverGroupName)) {
      errors.rejectValue "serverGroupName", "${descriptionName}.serverGroupName.invalid"
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
