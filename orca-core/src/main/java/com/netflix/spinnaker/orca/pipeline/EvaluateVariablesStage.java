/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.EvaluateVariablesTask;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class EvaluateVariablesStage implements StageDefinitionBuilder {

  public static String STAGE_TYPE = "evaluateVariables";

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("evaluateVariables", EvaluateVariablesTask.class);
  }

  public static final class EvaluateVariablesStageContext {
    private final List<Variable> vars;

    @JsonCreator
    public EvaluateVariablesStageContext(
        @JsonProperty("variables") @Nullable List<Variable> variables) {
      this.vars = variables;
    }

    public @Nullable List<Variable> getVariables() {
      return vars;
    }
  }

  public static class Variable {
    private String key;
    private String value;

    @JsonCreator
    public Variable(@JsonProperty("key") String key, @JsonProperty("value") String value) {
      this.key = key;
      this.value = value;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }
}
