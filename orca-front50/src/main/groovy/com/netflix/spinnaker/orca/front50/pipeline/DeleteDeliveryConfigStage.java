package com.netflix.spinnaker.orca.front50.pipeline;

import com.netflix.spinnaker.orca.front50.tasks.DeleteDeliveryConfigTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class DeleteDeliveryConfigStage implements StageDefinitionBuilder {
  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("deleteDeliveryConfig", DeleteDeliveryConfigTask.class);
  }
}
