package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public class SimpleStage implements StageDefinitionBuilder {

  private final String type;
  private final Task task;

  public SimpleStage(String type, Task task) {
    this.type = type;
    this.task = task;
  }

  @Override public String getType() {
    return type;
  }

  @Override
  public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder.withTask("task", task.getClass());
  }
}
