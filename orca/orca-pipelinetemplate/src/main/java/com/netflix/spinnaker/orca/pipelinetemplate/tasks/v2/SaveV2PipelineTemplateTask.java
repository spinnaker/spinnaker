/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.tasks.v2;

import static com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate.Metadata.TEMPLATE_VALID_NAME_REGEX;
import static com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate.Variable.TEMPLATE_VALID_VARIABLE_NAME_REGEX;

import com.google.common.base.Strings;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface SaveV2PipelineTemplateTask {

  default void validate(V2PipelineTemplate template) {
    // TODO(jacobkiefer): Codify in a regex.
    if (template.getId().contains(".")) {
      throw new IllegalArgumentException("Pipeline Template IDs cannot have dots");
    }

    List<String> missingFields = new ArrayList<>();
    if (Strings.isNullOrEmpty(template.getMetadata().getName())) {
      missingFields.add("metadata.name");
    }
    if (Strings.isNullOrEmpty(template.getMetadata().getDescription())) {
      missingFields.add("metadata.description");
    }
    if (template.getMetadata().getScopes() == null
        || template.getMetadata().getScopes().isEmpty()) {
      missingFields.add("metadata.scopes");
    }
    if (Strings.isNullOrEmpty(template.getSchema())) {
      missingFields.add("schema");
    }

    if (!missingFields.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing required fields: " + String.join(",", missingFields));
    }

    if (!template.getSchema().equals(V2PipelineTemplate.V2_SCHEMA_VERSION)) {
      throw new IllegalArgumentException(
          String.format("Invalid schema version: %s", template.getSchema()));
    }

    if (!template.getMetadata().getName().matches(TEMPLATE_VALID_NAME_REGEX)) {
      throw new IllegalArgumentException(
          String.format(
              "Illegal template name: %s. Name must match the regex: %s",
              template.getMetadata().getName(), TEMPLATE_VALID_NAME_REGEX));
    }

    if (template.getVariables() != null) {
      List<String> invalidVariableNames =
          template.getVariables().stream()
              .filter(
                  variable ->
                      variable.getName() != null
                          && !variable.getName().matches(TEMPLATE_VALID_VARIABLE_NAME_REGEX))
              .map(V2PipelineTemplate.Variable::getName)
              .collect(Collectors.toList());

      if (!invalidVariableNames.isEmpty()) {
        throw new IllegalArgumentException(
            String.format(
                "Illegal variable names: %s. Variable names must match the regex: %s",
                String.join(", ", invalidVariableNames), TEMPLATE_VALID_VARIABLE_NAME_REGEX));
      }
    }
  }
}
