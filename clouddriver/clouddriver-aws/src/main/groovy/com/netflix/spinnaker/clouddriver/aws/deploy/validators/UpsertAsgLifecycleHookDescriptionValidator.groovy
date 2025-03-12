/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgLifecycleHookDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import org.springframework.stereotype.Component

@Component("upsertAsgLifecycleHookDescriptionValidator")
class UpsertAsgLifecycleHookDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertAsgLifecycleHookDescription> {

  @Override
  void validate(List priorDescriptions, UpsertAsgLifecycleHookDescription description, ValidationErrors errors) {
    validateRegions(description, [description.region], "upsertAsgLifecycleHookDescription", errors)

    if (!description.serverGroupName) {
      rejectNull("serverGroupName", errors)
    }

    if (!description.roleARN) {
      rejectNull("roleARN", errors)
    }

    if (!description.notificationTargetARN) {
      rejectNull("notificationTargetARN", errors)
    }

    if (description.heartbeatTimeout > 3600) {
      errors.rejectValue(
        "heartbeatTimeout",
        "upsertAsgLifecycleHookDescription.heartbeatTimeout.invalid",
        "Heartbeat Timeout cannot be greater than 3600"
      )
    }
  }

  static void rejectNull(String field, ValidationErrors errors) {
    errors.rejectValue(field, "upsertAsgLifecycleHookDescription.${field}.not.nullable")
  }
}
