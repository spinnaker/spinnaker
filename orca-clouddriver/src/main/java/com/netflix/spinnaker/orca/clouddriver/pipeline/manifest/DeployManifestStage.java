/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Collections.emptyMap;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestCoordinates;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.CleanupArtifactsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeployManifestContext.TrafficManagement;
import com.netflix.spinnaker.orca.pipeline.ExpressionAwareStageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeployManifestStage extends ExpressionAwareStageDefinitionBuilder {

  public static final String PIPELINE_CONFIG_TYPE = "deployManifest";

  private final OldManifestActionAppender oldManifestActionAppender;

  private static boolean shouldRemoveStageOutputs(@NotNull StageExecution stage) {
    return stage.getContext().getOrDefault("noOutput", "false").toString().equals("true");
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask(ResolveDeploySourceManifestTask.TASK_NAME, ResolveDeploySourceManifestTask.class)
        .withTask(DeployManifestTask.TASK_NAME, DeployManifestTask.class)
        .withTask(MonitorDeployManifestTask.TASK_NAME, MonitorDeployManifestTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(WaitForManifestStableTask.TASK_NAME, WaitForManifestStableTask.class)
        .withTask(CleanupArtifactsTask.TASK_NAME, CleanupArtifactsTask.class)
        .withTask("monitorCleanup", MonitorKatoTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
  }

  @Override
  public boolean processExpressions(
      @Nonnull StageExecution stage,
      @Nonnull ContextParameterProcessor contextParameterProcessor,
      @Nonnull ExpressionEvaluationSummary summary) {
    DeployManifestContext context = stage.mapTo(DeployManifestContext.class);
    if (context.isSkipExpressionEvaluation()) {
      processDefaultEntries(
          stage, contextParameterProcessor, summary, Collections.singletonList("manifests"));
      return false;
    }
    return true;
  }

  @Override
  public void afterStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    TrafficManagement trafficManagement =
        stage.mapTo(DeployManifestContext.class).getTrafficManagement();
    if (trafficManagement.isEnabled()) {
      switch (trafficManagement.getOptions().getStrategy()) {
        case RED_BLACK:
        case BLUE_GREEN:
          oldManifestActionAppender.deleteOrDisableOldManifest(stage.getContext(), graph);
          break;
        case HIGHLANDER:
          oldManifestActionAppender.disableOldManifest(stage.getContext(), graph);
          oldManifestActionAppender.deleteOldManifest(stage.getContext(), graph);
          break;
        case NONE:
          // do nothing
      }
    }
    if (shouldRemoveStageOutputs(stage)) {
      stage.setOutputs(emptyMap());
    }
  }

  /** {@code OldManifestActionAppender} appends new stages to old manifests */
  @Component
  @RequiredArgsConstructor
  static class OldManifestActionAppender {

    private final GetDeployedManifests deployedManifests;
    private final ManifestOperationsHelper manifestOperationsHelper;

    /**
     * Appends delete stages to already deployed manifests that preceded the current stage manifest
     *
     * @param parentContext of currently executed stage
     * @param graph stage execution graph
     */
    private void deleteOldManifest(Map<String, Object> parentContext, StageGraphBuilder graph) {
      applyAction(
          parentContext,
          (name, manifest) ->
              appendStage(graph, manifest, name, DeleteManifestStage.PIPELINE_CONFIG_TYPE));
    }

    /**
     * Appends disable stages to already deployed manifests that preceded the current stage manifest
     *
     * @param parentContext of currently executed stage
     * @param graph stage execution graph
     */
    private void disableOldManifest(Map<String, Object> parentContext, StageGraphBuilder graph) {
      applyAction(
          parentContext,
          (name, manifest) ->
              appendStage(graph, manifest, name, DisableManifestStage.PIPELINE_CONFIG_TYPE));
    }

    /**
     * Appends disable or delete stages to already deployed manifests that preceded the current
     * stage manifest. The specific stage that will be appended depends on the status of the
     * previous deployment.
     *
     * @param parentContext of currently executed stage
     * @param graph stage execution graph
     */
    private void deleteOrDisableOldManifest(
        Map<String, Object> parentContext, StageGraphBuilder graph) {
      applyAction(
          parentContext,
          (name, manifest) -> {
            var oldManifestIsUnstable =
                this.manifestOperationsHelper.previousDeploymentNeitherStableNorFailed(
                    manifest.getAccount(), manifest.getNamespace(), name);
            var nextStageType =
                oldManifestIsUnstable
                    ? DeleteManifestStage.PIPELINE_CONFIG_TYPE
                    : DisableManifestStage.PIPELINE_CONFIG_TYPE;
            appendStage(graph, manifest, name, nextStageType);
          });
    }

    private void applyAction(
        Map<String, Object> parentContext, BiConsumer<String, DeployedManifest> action) {
      this.deployedManifests
          .get(parentContext)
          .forEach(
              currentlyDeployedManifest ->
                  manifestOperationsHelper
                      .getOldManifestNames(currentlyDeployedManifest)
                      .forEach(
                          oldManifestName ->
                              action.accept(oldManifestName, currentlyDeployedManifest)));
    }

    private void appendStage(
        StageGraphBuilder graph, DeployedManifest manifest, String name, String stageType) {
      graph.append(
          (stage) -> {
            stage.setType(stageType);
            Map<String, Object> context = stage.getContext();
            context.put("account", manifest.getAccount());
            context.put("app", manifest.getApplication());
            context.put("cloudProvider", manifest.getCloudProvider());
            context.put("manifestName", name);
            context.put("location", manifest.getNamespace());
          });
    }
  }

  /**
   * Delegate class to handle all manifest-related actions in this file such as fetching manifest
   * from external service or extracting manifest params from parentContext
   */
  @Component
  @RequiredArgsConstructor
  static class ManifestOperationsHelper {

    private static final String REPLICA_SET = "replicaSet";
    private static final String KIND = "kind";
    private static final String OUTPUTS_MANIFEST = "outputs.manifests";

    private final OortService oortService;

    /**
     * This returns all replicaSet manifests from the cluster. The search is performed in an
     * external service, and search parameters match manifests deployed in the current stage.
     *
     * @param dm - deployment manifest of current stage
     * @return list of all manifest already deployed to the cluster
     */
    ImmutableList<String> getOldManifestNames(DeployedManifest dm) {
      return oortService
          .getClusterManifests(
              dm.getAccount(),
              dm.getNamespace(),
              REPLICA_SET,
              dm.getApplication(),
              dm.getClusterName())
          .stream()
          .filter(m -> !m.getFullResourceName().equals(dm.getManifestName()))
          .map(ManifestCoordinates::getFullResourceName)
          .collect(toImmutableList());
    }

    /**
     * Returns replicaSet manifests from the {@code parentContext}
     *
     * @param parentContext of currently processed stage
     * @return list of replicaSet manifests deployed in the cluster - obtained directly from the
     *     {@code parentContext}
     */
    List<Map<String, ?>> getNewManifests(Map<String, Object> parentContext) {
      var manifestsFromParentContext = (List<Map<String, ?>>) parentContext.get(OUTPUTS_MANIFEST);
      return manifestsFromParentContext.stream()
          .filter(manifest -> REPLICA_SET.equalsIgnoreCase((String) manifest.get(KIND)))
          .collect(Collectors.toList());
    }

    /**
     * During a B/G deployment, if the blue deployment fails to create pods (due to issues such as
     * an incorrect image name), the deployment will not fail, but will wait indefinitely to achieve
     * stability. This is indicated by status.failed=false and status.stable=false. This method
     * checks for such a situation.
     *
     * @param account used to run deployment
     * @param name of the manifest
     * @return true, if manifest was not deployed correctly and waits to get stable, false otherwise
     */
    boolean previousDeploymentNeitherStableNorFailed(String account, String location, String name) {
      var oldManifest = this.oortService.getManifest(account, location, name, false);

      Map<String, Double> statusSpec =
          (Map<String, Double>) oldManifest.getManifest().getOrDefault("status", emptyMap());
      if (statusSpec.containsKey("readyReplicas") && statusSpec.containsKey("availableReplicas")) {
        var readyReplicas = statusSpec.get("readyReplicas");
        var availableReplicas = statusSpec.get("availableReplicas");
        if (readyReplicas > 0 && availableReplicas > 0) {
          return false;
        }
      }

      var status = oldManifest.getStatus();
      var notStable = !status.getStable().isState();
      var notFailed = !status.getFailed().isState();

      return notFailed && notStable;
    }
  }

  /** Delegate class for fetching and mapping manifests deployed in the cluster */
  @Component
  @RequiredArgsConstructor
  static class GetDeployedManifests {

    private final ManifestOperationsHelper manifestOperationsHelper;

    /**
     * This method encapsulates fetching deployed manifests and mapping them to a new designated
     * type, {@code DeployedManifest}
     *
     * @param parentContext is the context of currently processed stage
     * @return list of replicaSet manifests deployed in currently processed stage
     */
    List<DeployedManifest> get(Map<String, Object> parentContext) {

      var deployedManifests = new ArrayList<DeployedManifest>();
      var account = (String) parentContext.get("account");
      var manifestMoniker = (Map) parentContext.get("moniker");
      var application = (String) manifestMoniker.get("app");

      this.manifestOperationsHelper
          .getNewManifests(parentContext)
          .forEach(
              manifest -> {
                var manifestMetadata = (Map) manifest.get("metadata");
                var annotations = (Map) manifestMetadata.get("annotations");

                deployedManifests.add(
                    new DeployedManifest(
                        account,
                        manifestMoniker,
                        application,
                        (Map) manifest.get("metadata"),
                        String.format("replicaSet %s", manifestMetadata.get("name")),
                        (String) manifestMetadata.get("namespace"),
                        (Map) manifestMetadata.get("annotations"),
                        (String) annotations.get("moniker.spinnaker.io/cluster"),
                        "kubernetes"));
              });
      return deployedManifests;
    }
  }

  @Getter
  @RequiredArgsConstructor
  static class DeployedManifest {
    private final String account;
    private final Map manifestMoniker;
    private final String application;
    private final Map manifestMetadata;
    private final String manifestName;
    private final String namespace;
    private final Map annotations;
    private final String clusterName;
    private final String cloudProvider;
  }
}
