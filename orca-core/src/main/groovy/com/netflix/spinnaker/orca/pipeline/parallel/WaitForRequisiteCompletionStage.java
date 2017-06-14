package com.netflix.spinnaker.orca.pipeline.parallel;

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import groovy.transform.CompileStatic;
import org.springframework.stereotype.Component;

@Deprecated
@CompileStatic
@Component
public class WaitForRequisiteCompletionStage implements StageDefinitionBuilder {
  @Override
  public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder.withTask("waitForRequisiteTasks", WaitForRequisiteCompletionTask.class);
  }

  public static final String PIPELINE_CONFIG_TYPE = StageDefinitionBuilderSupport.getType(WaitForRequisiteCompletionStage.class);
}
