/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.api.SimpleStage;
import com.netflix.spinnaker.orca.api.SimpleStageInput;
import com.netflix.spinnaker.orca.api.SimpleStageOutput;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimpleTask implements Task {
  private SimpleStage simpleStage;

  SimpleTask(@Nullable SimpleStage simpleStage) {
    this.simpleStage = simpleStage;
  }

  private SimpleStageInput getStageInput(Stage stage) {
    ObjectMapper objectMapper = OrcaObjectMapper.newInstance();

    try {
      List<Class<?>> cArg = Arrays.asList(SimpleStageInput.class);
      Method method = simpleStage.getClass().getMethod("execute", cArg.toArray(new Class[0]));
      Type inputType = ResolvableType.forMethodParameter(method, 0).getGeneric().getType();
      Map<TypeVariable, Type> typeVariableMap =
          GenericTypeResolver.getTypeVariableMap(simpleStage.getClass());

      return new SimpleStageInput(
          objectMapper.convertValue(
              stage.getContext(), GenericTypeResolver.resolveType(inputType, typeVariableMap)));
    } catch (NoSuchMethodException exeception) {
      throw new NoSuchStageException(exeception.getMessage());
    }
  }

  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    ObjectMapper objectMapper = OrcaObjectMapper.newInstance();
    SimpleStageInput simpleStageInput = getStageInput(stage);
    SimpleStageOutput output = simpleStage.execute(simpleStageInput);
    ExecutionStatus status = ExecutionStatus.valueOf(output.getStatus().toString());

    return TaskResult.builder(status)
        .context(
            output.getContext() == null
                ? new HashMap<>()
                : objectMapper.convertValue(output.getContext(), Map.class))
        .outputs(
            output.getOutput() == null
                ? new HashMap<>()
                : objectMapper.convertValue(output.getOutput(), Map.class))
        .build();
  }
}
