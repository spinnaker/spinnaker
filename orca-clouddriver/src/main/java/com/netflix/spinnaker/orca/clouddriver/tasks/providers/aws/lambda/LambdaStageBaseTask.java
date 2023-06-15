/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public interface LambdaStageBaseTask extends Task {

  default boolean validateInput(StageExecution stage, List<String> errors) {
    return true;
  }

  default void prepareTask(StageExecution stage) {
    if (stage.getOutputs() == null) {
      stage.setOutputs(new HashMap<>());
    }
    if (stage.getContext().get("taskContext") == null) {
      stage.getContext().put("taskContext", new HashMap<String, Object>());
    }
  }

  default void addToTaskContext(StageExecution stage, String key, Object value) {
    ((Map<String, Object>) stage.getContext().get("taskContext")).put(key, value);
  }

  /**
   * In verify tasks, copy stuff from context to output if the cloudDriver operation succeeded If we
   * get all the outputs from the cloudoperation results, this may not be needed. Meanwhile, during
   * a task, we copy potential required outputs to context and then during the verification of that
   * task, if (and only if) the task was successful, we copy the context to output
   *
   * @param stage
   */
  default void copyContextToOutput(StageExecution stage) {
    Map c = (Map<String, Object>) stage.getContext().get("taskContext");
    c.forEach((x, y) -> stage.getOutputs().put((String) x, y));
  }

  default Map<String, Object> getTaskContext(StageExecution stage) {
    return (Map<String, Object>) stage.getContext().get("taskContext");
  }

  default void addToOutput(StageExecution stage, String key, Object value) {
    stage.getOutputs().put(key, value);
  }

  default void addErrorMessage(StageExecution stage, String errorMesssage) {
    stage.getOutputs().put("failureMessage", errorMesssage);
  }

  default void logException(StageExecution stage, Throwable e) {
    // TODO: Print the entire stage context etc.
    e.printStackTrace();
  }

  default void addExceptionToOutput(StageExecution stage, Throwable e) {
    stage.getOutputs().put("failureMessage", e.getMessage());
  }

  default void addCloudOperationToContext(StageExecution stage, LambdaCloudOperationOutput ldso) {
    addCloudOperationToContext(stage, ldso, "url");
  }

  default void addCloudOperationToContext(
      StageExecution stage, LambdaCloudOperationOutput ldso, String urlKey) {
    String url = ldso.getUrl() != null ? ldso.getUrl() : "";
    this.addToTaskContext(stage, urlKey, url);
  }

  default TaskResult taskComplete(StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(getTaskContext(stage))
        .outputs(stage.getOutputs())
        .build();
  }

  default TaskResult formTaskResult(
      StageExecution stage, LambdaCloudOperationOutput ldso, Map<String, Object> outputMap) {
    addCloudOperationToContext(stage, ldso);
    if (outputMap == null) {
      outputMap = new HashMap<>();
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(getTaskContext(stage))
        .outputs(outputMap)
        .build();
  }

  default TaskResult formSuccessTaskResult(
      StageExecution stage, String taskName, String successMessage) {
    addToOutput(stage, "status:" + taskName, successMessage);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(stage.getOutputs()).build();
  }

  default TaskResult formErrorTaskResult(StageExecution stage, String errorMessage) {
    addErrorMessage(stage, errorMessage);
    return TaskResult.builder(ExecutionStatus.TERMINAL).outputs(stage.getOutputs()).build();
  }

  default TaskResult formErrorListTaskResult(StageExecution stage, List<String> errorMessages) {
    errorMessages.removeAll(Collections.singleton(null));
    String errorMessage = StringUtils.join(errorMessages, "\n");
    return formErrorTaskResult(stage, errorMessage);
  }
}
