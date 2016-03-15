package com.netflix.spinnaker.orca;

import java.io.Serializable;
import com.google.common.collect.ImmutableMap;

public interface TaskResult {
  ExecutionStatus getStatus();

  @Deprecated ImmutableMap<String, Serializable> getOutputs();

  ImmutableMap<String, Serializable> getStageOutputs();

  ImmutableMap<String, Serializable> getGlobalOutputs();
}
