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
import com.netflix.spinnaker.kork.plugins.proxy.ExtensionClassProvider;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.api.SimpleStage;
import com.netflix.spinnaker.orca.api.SimpleStageInput;
import com.netflix.spinnaker.orca.api.SimpleStageOutput;
import com.netflix.spinnaker.orca.api.SimpleStageStatus;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
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

  private SimpleStageInput getStageInput(Stage stage, ObjectMapper objectMapper) {
    try {
      Class<?> extensionClass = ExtensionClassProvider.getExtensionClass(simpleStage);
      List<Class<?>> cArg = Arrays.asList(SimpleStageInput.class);
      Method method = extensionClass.getMethod("execute", cArg.toArray(new Class[0]));
      Type inputType = ResolvableType.forMethodParameter(method, 0).getGeneric().getType();
      Map<TypeVariable, Type> typeVariableMap =
          GenericTypeResolver.getTypeVariableMap(extensionClass);
      Class<?> resolvedType = GenericTypeResolver.resolveType(inputType, typeVariableMap);

      return new SimpleStageInput(objectMapper.convertValue(stage.getContext(), resolvedType));
    } catch (NoSuchMethodException exeception) {
      throw new NoSuchStageException(exeception.getMessage());
    }
  }

  @Nonnull
  public TaskResult execute(@Nonnull Stage stage) {
    ObjectMapper objectMapper = OrcaObjectMapper.newInstance();
    SimpleStageInput simpleStageInput = getStageInput(stage, objectMapper);
    SimpleStageOutput output = simpleStage.execute(simpleStageInput);
    ExecutionStatus status =
        ExecutionStatus.valueOf(
            Optional.ofNullable(output.getStatus()).orElse(SimpleStageStatus.RUNNING).toString());

    return TaskResult.builder(status)
        .context(
            output.getContext() == null
                ? new HashMap<>()
                : objectMapper.convertValue(
                    Optional.ofNullable(output.getContext()).orElse(Collections.emptyMap()),
                    Map.class))
        .outputs(
            output.getOutput() == null
                ? new HashMap<>()
                : objectMapper.convertValue(
                    Optional.ofNullable(output.getOutput()).orElse(Collections.emptyMap()),
                    Map.class))
        .build();
  }
}
