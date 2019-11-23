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

import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import lombok.Data;

@Data
public class ExpressionSpelEvaluatorDefinition {
  private String versionKey;
  private String description;
  private boolean isDeprecated;

  public ExpressionSpelEvaluatorDefinition() {}

  public ExpressionSpelEvaluatorDefinition(
      PipelineExpressionEvaluator.SpelEvaluatorVersion version) {
    this.versionKey = version.getKey();
    this.description = version.getDescription();
    this.isDeprecated = version.isDeprecated();
  }
}
