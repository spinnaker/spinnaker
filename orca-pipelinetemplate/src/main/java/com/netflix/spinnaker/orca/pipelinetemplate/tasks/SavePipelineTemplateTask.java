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
package com.netflix.spinnaker.orca.pipelinetemplate.tasks;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface SavePipelineTemplateTask {

  default void validate(PipelineTemplate template) {
    if (template.getId().contains(".")) {
      throw new IllegalArgumentException("Pipeline Template IDs cannot have dots");
    }

    List<String> missingFields = new ArrayList<>();
    if (template.getMetadata().getName() == null
        || "".equalsIgnoreCase(template.getMetadata().getName())) {
      missingFields.add("metadata.name");
    }
    if (template.getMetadata().getDescription() == null
        || "".equalsIgnoreCase(template.getMetadata().getDescription())) {
      missingFields.add("metadata.description");
    }
    if (template.getMetadata().getScopes() == null
        || template.getMetadata().getScopes().isEmpty()) {
      missingFields.add("metadata.scopes");
    }

    if (!missingFields.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing required fields: " + String.join(",", missingFields));
    }

    if (template.getVariables() != null) {
      List<String> invalidVariableNames =
          template.getVariables().stream()
              .filter(variable -> variable.getName() != null && variable.getName().contains("-"))
              .map(Variable::getName)
              .collect(Collectors.toList());

      if (!invalidVariableNames.isEmpty()) {
        throw new IllegalArgumentException(
            "Variable names cannot include dashes (-)."
                + " Invalid variable names: "
                + String.join(", ", invalidVariableNames));
      }
    }
  }
}
