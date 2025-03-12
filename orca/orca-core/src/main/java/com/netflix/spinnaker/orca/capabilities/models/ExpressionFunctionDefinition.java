/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.capabilities.models;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ExpressionFunctionDefinition {
  private String name;
  private String description;
  private List<ExpressionFunctionParameterDefinition> parameters;
  private ExpressionFunctionProvider.FunctionDocumentation documentation;

  public ExpressionFunctionDefinition() {}

  public ExpressionFunctionDefinition(
      String namespace, ExpressionFunctionProvider.FunctionDefinition functionDefinition) {
    if (Strings.isNullOrEmpty(namespace)) {
      name = functionDefinition.getName();
    } else {
      name = namespace + "_" + functionDefinition.getName();
    }

    description = functionDefinition.getDescription();

    parameters = new ArrayList<>();
    for (ExpressionFunctionProvider.FunctionParameter parameter :
        functionDefinition.getParameters()) {
      if (parameter.getType() != PipelineExecution.class) {
        parameters.add(new ExpressionFunctionParameterDefinition(parameter));
      }
    }

    documentation = functionDefinition.getDocumentation();
  }
}
