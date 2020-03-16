package com.netflix.spinnaker.orca.front50.pipeline;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.tasks.MonitorFront50Task;
import com.netflix.spinnaker.orca.front50.tasks.UpsertDeliveryConfigTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class UpsertDeliveryConfigStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("upsertDeliveryConfig", UpsertDeliveryConfigTask.class)
        .withTask("monitorUpsert", MonitorFront50Task.class);
  }
}
