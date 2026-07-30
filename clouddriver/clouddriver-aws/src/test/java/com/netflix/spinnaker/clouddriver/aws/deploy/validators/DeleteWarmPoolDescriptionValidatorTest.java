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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class DeleteWarmPoolDescriptionValidatorTest {

  DeleteWarmPoolDescriptionValidator validator = new DeleteWarmPoolDescriptionValidator();

  private AsgDescription asg(String name, String region) {
    AsgDescription asg = new AsgDescription();
    asg.setServerGroupName(name);
    asg.setRegion(region);
    return asg;
  }

  @Test
  void passValidationWithProperInputs() {
    DeleteWarmPoolDescription description = new DeleteWarmPoolDescription();
    description.setAsgs(Collections.singletonList(asg("asg1", "us-west-1")));
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verifyNoInteractions(errors);
  }

  @Test
  void missingAsgsMissingFailsValidation() {
    DeleteWarmPoolDescription description = new DeleteWarmPoolDescription();
    description.setAsgs(Collections.emptyList());
    ValidationErrors errors = mock(ValidationErrors.class);

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("asgs", "DeleteWarmPoolDescription.empty");
  }
}
