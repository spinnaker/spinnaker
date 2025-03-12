/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpdateInstancesDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.UPDATE_INSTANCES)
@Component("updateInstancesDescriptionValidator")
class UpdateInstancesDescriptionValidator extends AmazonDescriptionValidationSupport<UpdateInstancesDescription> {

  @Override
  void validate(List priorDescriptions, UpdateInstancesDescription description, ValidationErrors errors) {
    if (!description.serverGroupName) {
      errors.rejectValue("name", "updateSecurityGroupsDescription.name.not.nullable")
    }
    if (!description.region) {
      errors.rejectValue("region", "updateSecurityGroupsDescription.region.not.nullable")
    }
    if (!description.securityGroups && description.securityGroupsAppendOnly) {
      errors.rejectValue("securityGroups", "updateSecurityGroupsDescription.securityGroups.not.nullable")
    }
  }
}
