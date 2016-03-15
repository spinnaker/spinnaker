package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.pipeline.model.Stage;

public interface Task {
  TaskResult execute(Stage stage);
}
