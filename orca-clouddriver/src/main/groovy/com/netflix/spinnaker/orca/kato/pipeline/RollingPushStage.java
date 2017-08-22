package com.netflix.spinnaker.orca.kato.pipeline;

import java.util.Map;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.TerminateInstancesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForDownInstanceHealthTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForTerminatedInstancesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CaptureParentInterestingHealthProviderNamesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.kato.tasks.DisableInstancesTask;
import com.netflix.spinnaker.orca.kato.tasks.rollingpush.*;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static java.lang.String.format;

@Component
public class RollingPushStage implements StageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "rollingPush";

  @Autowired
  private FeaturesService featuresService;

  @Override
  public <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    boolean taggingEnabled = featuresService.isStageAvailable("upsertEntityTags");
    builder
      .withTask("captureParentInterestingHealthProviderNames", CaptureParentInterestingHealthProviderNamesTask.class)
      .withTask("determineTerminationCandidates", DetermineTerminationCandidatesTask.class)
      .withLoop(subGraph -> {
          subGraph
            .withTask("determineCurrentPhaseTerminations", DetermineTerminationPhaseInstancesTask.class);

          if (shouldWaitForTermination(stage)) {
            subGraph.withTask("wait", WaitTask.class);
          }

          subGraph
            .withTask("disableInstances", DisableInstancesTask.class)
            .withTask("monitorDisable", MonitorKatoTask.class)
            .withTask("waitForDisabledState", WaitForDownInstanceHealthTask.class)
            .withTask("terminateInstances", TerminateInstancesTask.class)
            .withTask("waitForTerminateOperation", MonitorKatoTask.class)
            .withTask("waitForTerminatedInstances", WaitForTerminatedInstancesTask.class)
            .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class)
            .withTask("waitForNewInstances", WaitForNewUpInstancesLaunchTask.class)
            .withTask("checkForRemainingTerminations", CheckForRemainingTerminationsTask.class);
        });

    if (taggingEnabled) {
      builder
        .withTask("cleanUpTags", CleanUpTagsTask.class)
        .withTask("monitorTagCleanUp", MonitorKatoTask.class);
    }

    builder.withTask("pushComplete", PushCompleteTask.class);
  }

  private <T extends Execution<T>> boolean shouldWaitForTermination(Stage<T> stage) {
    Map termination = (Map) stage.getContext().get("termination");
    return termination != null && termination.containsKey("waitTime");
  }

  @Component
  public static class PushCompleteTask implements com.netflix.spinnaker.orca.Task {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override public TaskResult execute(Stage stage) {
      log.info(format(
        "Rolling Push completed for %s in %s / %s",
        stage.getContext().get("asgName"),
        stage.getContext().get("account"),
        stage.getContext().get("region")
      ));
      return TaskResult.SUCCEEDED;
    }
  }
}
