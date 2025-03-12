/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.OrcaService.Orca;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

@Component
public class OrcaRunner {
  @Autowired private ObjectMapper objectMapper;

  public void monitorTask(Supplier<String> submitTask, Orca orca) {
    final String id = getTaskEndpoint(submitTask);

    Supplier<Pipeline> getPipeline =
        () -> {
          Map<String, Object> execution = orca.getRef(id);
          return objectMapper.convertValue(execution, Task.class).getExecution();
        };

    monitor(getPipeline);
  }

  public void monitorPipeline(Supplier<String> submitPipeline, Orca orca) {
    final String id = getTaskEndpoint(submitPipeline);

    Supplier<Pipeline> getPipeline =
        () -> {
          Map<String, Object> execution = orca.getRef(id);
          return objectMapper.convertValue(execution, Pipeline.class);
        };

    monitor(getPipeline);
  }

  private String getTaskEndpoint(Supplier<String> submitter) {
    return submitter.get().substring(1);
  }

  private static Problem findExecutionError(Pipeline pipeline) {
    Problem problem = findError(pipeline);
    if (problem == null) {
      for (Pipeline.Stage stage : pipeline.getStages()) {
        problem = findError(stage);
        if (problem != null) {
          break;
        }
      }
    }

    if (problem == null) {
      problem =
          new ProblemBuilder(Problem.Severity.FATAL, "Pipeline failed, but no error was found.")
              .build();
    }
    return problem;
  }

  private static Problem findError(HasContext hasContext) {
    if (hasContext == null) {
      return null;
    }

    Context context = hasContext.getContext();
    if (context == null) {
      return null;
    }

    ExecutionException exception = context.getException();
    if (exception == null) {
      return null;
    }

    StringBuilder message = new StringBuilder();

    String operation = exception.getOperation();
    if (operation != null) {
      message.append(formatId(operation)).append(": ");
    }

    ExecutionException.Details details = exception.getDetails();
    if (details == null) {
      message.append("No error details found.");
    } else {
      message.append("(").append(details.getError()).append(") ");

      for (String error : details.getErrors()) {
        message.append(error).append(" ");
      }
    }

    return new ProblemBuilder(Problem.Severity.FATAL, message.toString()).build();
  }

  private void monitor(Supplier<Pipeline> getPipeline) {
    String status;
    Pipeline pipeline;
    Set<String> loggedTasks = new HashSet<>();
    try {
      pipeline = getPipeline.get();
      status = pipeline.getStatus();

      while (status.equalsIgnoreCase("running") || status.equalsIgnoreCase("not_started")) {
        logPipelineOutput(pipeline, loggedTasks);
        DaemonTaskHandler.safeSleep(TimeUnit.SECONDS.toMillis(10));
        pipeline = getPipeline.get();
        status = pipeline.getStatus();
      }
    } catch (RetrofitError e) {
      throw new HalException(
          new ProblemBuilder(Problem.Severity.FATAL, "Failed to monitor task: " + e.getMessage())
              .build());
    }

    logPipelineOutput(pipeline, loggedTasks);

    if (status.equalsIgnoreCase("terminal")) {
      Problem problem = findExecutionError(pipeline);
      throw new HalException(problem);
    }
  }

  private static void logPipelineOutput(Pipeline pipeline, Set<String> loggedTasks) {
    List<Pipeline.Stage> stages = pipeline.getStages();
    for (Pipeline.Stage stage : stages) {
      String stageName = formatId(stage.type != null ? stage.type : stage.id);
      for (Pipeline.Stage.Task task : stage.getTasks()) {
        String taskName = formatId(task.name != null ? task.name : task.id);
        String fullTaskId = stageName + ": " + taskName;
        String taskStatus = task.getStatus();
        if (!loggedTasks.contains(fullTaskId)
            && (taskStatus.equalsIgnoreCase("running")
                || taskStatus.equalsIgnoreCase("succeeded"))) {
          DaemonTaskHandler.message(taskName);
          loggedTasks.add(fullTaskId);
        }
      }
    }
  }

  private static String unCapitalize(String word) {
    if (word.length() == 0) {
      return word;
    }

    char first = word.toCharArray()[0];
    return Character.toLowerCase(first) + word.substring(1);
  }

  private static String capitalize(String word) {
    if (word.length() == 0) {
      return word;
    }

    char first = word.toCharArray()[0];
    return Character.toUpperCase(first) + word.substring(1);
  }

  private static String formatId(String id) {
    if (id.length() == 0) {
      return id;
    }

    id = capitalize(id);
    List<Integer> breaks = new ArrayList<>();
    char[] arr = id.toCharArray();
    for (int i = 0; i < arr.length; i++) {
      char c = arr[i];
      if (Character.isUpperCase(c)) {
        breaks.add(i);
      }
    }

    breaks.add(id.length());

    if (breaks.size() == 1) {
      return id;
    }

    List<String> words = new ArrayList<>();

    int last = breaks.remove(0);
    while (breaks.size() > 0) {
      int curr = breaks.remove(0);
      String word = id.substring(last, curr);
      if (last != 0) {
        word = unCapitalize(word);
      }
      words.add(word);
      last = curr;
    }

    return words.stream().reduce("", (a, b) -> a + " " + b).trim();
  }

  @Data
  static class HasContext {
    Context context;
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  static class Task extends HasContext {
    String status;
    List<Step> steps;
    Context context;
    Pipeline execution;

    @EqualsAndHashCode(callSuper = true)
    @Data
    static class Step extends Atom {}
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  static class Pipeline extends HasContext {
    String status;
    List<Stage> stages;

    @EqualsAndHashCode(callSuper = true)
    @Data
    static class Stage extends Atom {
      String parentId;
      String type;
      List<Task> tasks;

      @EqualsAndHashCode(callSuper = true)
      @Data
      static class Task extends Atom {}
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  static class Atom extends HasContext {
    String id;
    String name;
    String status;
    Long startTime;
    Long endTime;
  }

  @Data
  static class Context {
    ExecutionException exception;
  }

  @Data
  static class ExecutionException {
    String operation;
    Details details;

    @Data
    static class Details {
      String error;
      List<String> errors;
    }
  }
}
