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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AdjustmentType
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import org.springframework.stereotype.Component

@Component("upsertScalingPolicyDescriptionValidator")
class UpsertScalingPolicyDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertScalingPolicyDescription> {
  @Override
  void validate(List priorDescriptions, UpsertScalingPolicyDescription description, ValidationErrors errors) {
    validateRegions(description, [description.region], "upsertScalingPolicyDescription", errors)

    if (!description.serverGroupName && !description.asgName) {
      rejectNull "serverGroupName", errors
    }

    if (description.minAdjustmentMagnitude && !(description.adjustmentType == AdjustmentType.PercentChangeInCapacity)) {
      errors.rejectValue("minAdjustmentMagnitude", "upsertScalingPolicyDescription.minAdjustmentMagnitude.requires.adjustmentType.percentChangeInCapacity")
    }

    if ((description.simple && description.step) || (description.simple && description.targetTrackingConfiguration) || (description.step && description.targetTrackingConfiguration)) {
      errors.rejectValue("step", "upsertScalingPolicyDescription.must.only.specify.simple.step.targetTrackingConfiguration")
    }

    if (!description.simple && !description.step && !description.targetTrackingConfiguration) {
      errors.rejectValue("step", "upsertScalingPolicyDescription.must.be.simplePolicy.or.stepPolicy.or.targetTrackingPolicy")
    }

    if (description.targetTrackingConfiguration && !description.estimatedInstanceWarmup) {
      errors.rejectValue('estimatedInstanceWarmup', 'upsertScalingPolicyDescription.targetTracking.requires.estimatedInstanceWarmup')
    }
  }

  static void rejectNull(String field, ValidationErrors errors) {
    errors.rejectValue(field, "upsertScalingPolicyDescription.${field}.not.nullable")
  }
}
