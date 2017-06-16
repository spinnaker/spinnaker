package com.netflix.spinnaker.orca;

import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public interface Task {
  @Nonnull TaskResult execute(@Nonnull Stage stage);
}
