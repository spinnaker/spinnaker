package com.netflix.spinnaker.orca.clouddriver.pipeline.cache;

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.cache.ClouddriverClearAltTablespaceTask;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ClouddriverClearAltTablespaceStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull @NotNull TaskNode.Builder builder) {
    builder.withTask("clouddriverClearAltTablespace", ClouddriverClearAltTablespaceTask.class);
  }
}
