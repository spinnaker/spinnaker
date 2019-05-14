/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@EcsOperation(AtomicOperations.TERMINATE_INSTANCES)
@Component("ecsTerminateInstancesDescriptionValidator")
public class TerminateInstancesDescriptionValidator extends CommonValidator {
  public static final Pattern TASK_ID_PATTERN =
      Pattern.compile("[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}");

  public TerminateInstancesDescriptionValidator() {
    super("terminateInstancesDescription");
  }

  @Override
  public void validate(List priorDescriptions, Object description, Errors errors) {
    TerminateInstancesDescription typedDescription = (TerminateInstancesDescription) description;
    boolean validCredentials = validateCredentials(typedDescription, errors, "credentials");

    if (validCredentials) {
      validateRegions(
          typedDescription, Collections.singleton(typedDescription.getRegion()), errors, "region");
    }

    if (typedDescription.getEcsTaskIds() != null) {
      typedDescription
          .getEcsTaskIds()
          .forEach(
              taskId -> {
                if (!TASK_ID_PATTERN.matcher(taskId).find()) {
                  rejectValue(errors, "ecsTaskIds." + taskId, "invalid");
                }
              });
    } else {
      rejectValue(errors, "ecsTaskIds", "not.nullable");
    }
  }
}
