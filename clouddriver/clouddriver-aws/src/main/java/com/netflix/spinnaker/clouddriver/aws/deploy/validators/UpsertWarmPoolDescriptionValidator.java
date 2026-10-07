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
package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.autoscaling.model.WarmPoolState;

@Component
public class UpsertWarmPoolDescriptionValidator
    extends AmazonDescriptionValidationSupport<UpsertWarmPoolDescription> {

  @Override
  public void validate(
      List priorDescriptions, UpsertWarmPoolDescription description, ValidationErrors errors) {
    validateAsgs(description, errors);

    if (description.getMinSize() != null && description.getMinSize() < 0) {
      errors.rejectValue("minSize", "upsertWarmPoolDescription.minSize.invalid");
    }

    if (description.getMaxGroupPreparedCapacity() != null
        && description.getMinSize() != null
        && description.getMaxGroupPreparedCapacity() < description.getMinSize()) {
      errors.rejectValue(
          "maxGroupPreparedCapacity",
          "upsertWarmPoolDescription.maxGroupPreparedCapacity.transposed");
    }

    if (description.getPoolState() != null) {
      List<String> validStates =
          Arrays.stream(WarmPoolState.values())
              .map(WarmPoolState::toString)
              .collect(Collectors.toList());
      if (!validStates.contains(description.getPoolState())) {
        errors.rejectValue("poolState", "upsertWarmPoolDescription.poolState.not.valid");
      }
    }
  }
}
