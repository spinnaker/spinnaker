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

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ExpressionCapabilityResult {
  private List<ExpressionFunctionDefinition> functions;
  private List<ExpressionSpelEvaluatorDefinition> spelEvaluators;

  /**
   * Whether {@code ${...}} expressions consisting entirely of a hyphen-joined bareword (e.g. {@code
   * ${my-container-name}}) are resolved as a literal key lookup instead of failing due to SpEL
   * interpreting the hyphen(s) as subtraction operators. Reflects {@code
   * expression.dashed-identifiers.enabled}.
   */
  private boolean dashedIdentifiersEnabled;

  public ExpressionCapabilityResult() {
    functions = new ArrayList<>();
    spelEvaluators = new ArrayList<>();
  }
}
