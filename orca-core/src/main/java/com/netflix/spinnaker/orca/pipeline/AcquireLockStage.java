package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.AcquireLockTask;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class AcquireLockStage implements StageDefinitionBuilder {

  public static final String PIPELINE_TYPE = "acquireLock";

  @Nonnull
  @Override
  public String getType() {
    return PIPELINE_TYPE;
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("acquireLock", AcquireLockTask.class);
  }
}
