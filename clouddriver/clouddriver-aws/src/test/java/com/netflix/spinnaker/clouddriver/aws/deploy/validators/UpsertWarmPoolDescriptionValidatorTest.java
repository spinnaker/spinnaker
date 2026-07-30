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

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpsertWarmPoolDescriptionValidatorTest {

  UpsertWarmPoolDescriptionValidator validator = new UpsertWarmPoolDescriptionValidator();

  private AsgDescription asg(String name, String region) {
    AsgDescription asg = new AsgDescription();
    asg.setServerGroupName(name);
    asg.setRegion(region);
    return asg;
  }

  @Test
  void passValidationWithProperInputs() {
    UpsertWarmPoolDescription description = new UpsertWarmPoolDescription();
    description.setAsgs(Collections.singletonList(asg("asg1", "us-west-1")));
    description.setMinSize(1);
    description.setMaxGroupPreparedCapacity(5);
    description.setPoolState("Stopped");
    description.setReuseOnScaleIn(true);
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verifyNoInteractions(errors);
  }

  @Test
  void missingAsgsFailsValidation() {
    UpsertWarmPoolDescription description = new UpsertWarmPoolDescription();
    description.setAsgs(Collections.emptyList());
    description.setMinSize(1);
    description.setMaxGroupPreparedCapacity(5);
    description.setPoolState("Stopped");
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("asgs", "UpsertWarmPoolDescription.empty");
  }

  @Test
  void negativeMinSizeFailsValidation() {
    UpsertWarmPoolDescription description = new UpsertWarmPoolDescription();
    description.setAsgs(Collections.singletonList(asg("asg1", "us-west-1")));
    description.setMinSize(-1);
    description.setPoolState("Stopped");
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("minSize", "upsertWarmPoolDescription.minSize.invalid");
  }

  @Test
  void maxGroupPreparedCapacityLessThanMinSizeFailsValidation() {
    UpsertWarmPoolDescription description = new UpsertWarmPoolDescription();
    description.setAsgs(Collections.singletonList(asg("asg1", "us-west-1")));
    description.setMinSize(5);
    description.setMaxGroupPreparedCapacity(2);
    description.setPoolState("Stopped");
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors)
        .rejectValue(
            "maxGroupPreparedCapacity",
            "upsertWarmPoolDescription.maxGroupPreparedCapacity.transposed");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Sleeping", "running"})
  void invalidPoolStateFailsValidation(String poolState) {
    UpsertWarmPoolDescription description = new UpsertWarmPoolDescription();
    description.setAsgs(Collections.singletonList(asg("asg1", "us-west-1")));
    description.setPoolState(poolState);
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("poolState", "upsertWarmPoolDescription.poolState.not.valid");
  }
}
