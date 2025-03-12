package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.DefinedTask;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;

/** Resolves the task implementation for a given task node. */
public interface TaskImplementationResolver {

  default DefinedTask resolve(StageExecution stage, DefinedTask taskNode) {
    return taskNode;
  }
}
