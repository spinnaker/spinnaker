/*
 * Copyright 2026 McIntosh.farm
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

import com.amazonaws.services.autoscaling.model.WarmPoolState
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertWarmPoolDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import org.springframework.stereotype.Component

@Component
class UpsertWarmPoolDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertWarmPoolDescription> {
  @Override
  void validate(List priorDescriptions, UpsertWarmPoolDescription description, ValidationErrors errors) {
    validateAsgs description, errors

    if (description.minSize != null && description.minSize < 0) {
      errors.rejectValue "minSize", "upsertWarmPoolDescription.minSize.invalid"
    }

    if (description.maxGroupPreparedCapacity != null
      && description.minSize != null
      && description.maxGroupPreparedCapacity < description.minSize) {
      errors.rejectValue "maxGroupPreparedCapacity", "upsertWarmPoolDescription.maxGroupPreparedCapacity.transposed"
    }

    if (description.poolState && !WarmPoolState.values().any { it.toString() == description.poolState }) {
      errors.rejectValue "poolState", "upsertWarmPoolDescription.poolState.not.valid"
    }
  }
}
