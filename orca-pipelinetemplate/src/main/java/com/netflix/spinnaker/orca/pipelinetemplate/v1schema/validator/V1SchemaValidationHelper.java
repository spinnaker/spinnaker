/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;

import java.util.List;
import java.util.function.Function;

public class V1SchemaValidationHelper {

  public static void validateStageDefinitions(List<StageDefinition> stageDefinitions, Errors errors, Function<String, String> locationFormatter) {
    stageDefinitions.forEach(stageDefinition -> {
      if (stageDefinition.getId() == null) {
        errors.add(new Error()
          .withMessage("Stage ID is unset")
          .withLocation(locationFormatter.apply("stages"))
        );
      }

      if (stageDefinition.getType() == null) {
        errors.add(new Error()
          .withMessage("Stage is missing type")
          .withLocation(locationFormatter.apply("stages." + stageDefinition.getId()))
        );
      }

      if (stageDefinition.getConfig() == null) {
        errors.add(new Error()
          .withMessage("Stage configuration is unset")
          .withLocation(locationFormatter.apply("stages." + stageDefinition.getId()))
        );
      }

      if (stageDefinition.getDependsOn() != null && !stageDefinition.getDependsOn().isEmpty() &&
        stageDefinition.getInject() != null && stageDefinition.getInject().hasAny()) {
        errors.add(new Error()
          .withMessage("A stage cannot have both dependsOn and an inject rule defined simultaneously")
          .withLocation(locationFormatter.apply("stages." + stageDefinition.getId()))
        );
      }

      if (stageDefinition.getInject() != null && stageDefinition.getInject().hasMany()) {
        errors.add(new Error()
          .withMessage("A stage cannot have multiple inject rules defined")
          .withLocation(locationFormatter.apply("stages." + stageDefinition.getId()))
        );
      }
    });
  }
}
