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

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.CleanupArtifactsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeployManifestContext.TrafficManagement;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ResolveDeploySourceManifestTask;
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeployManifestStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "deployManifest";

  private final OortHelper oortHelper;

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask(ResolveDeploySourceManifestTask.TASK_NAME, ResolveDeploySourceManifestTask.class)
        .withTask(DeployManifestTask.TASK_NAME, DeployManifestTask.class)
        .withTask("monitorDeploy", MonitorKatoTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(ManifestForceCacheRefreshTask.TASK_NAME, ManifestForceCacheRefreshTask.class)
        .withTask(WaitForManifestStableTask.TASK_NAME, WaitForManifestStableTask.class)
        .withTask(CleanupArtifactsTask.TASK_NAME, CleanupArtifactsTask.class)
        .withTask("monitorCleanup", MonitorKatoTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(ManifestForceCacheRefreshTask.TASK_NAME, ManifestForceCacheRefreshTask.class)
        .withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
  }

  public void afterStages(@Nonnull Stage stage, @Nonnull StageGraphBuilder graph) {
    TrafficManagement trafficManagement =
        stage.mapTo(DeployManifestContext.class).getTrafficManagement();
    if (trafficManagement.isEnabled()) {
      switch (trafficManagement.getOptions().getStrategy()) {
        case RED_BLACK:
          disableOldManifests(stage.getContext(), graph);
          break;
        case HIGHLANDER:
          disableOldManifests(stage.getContext(), graph);
          deleteOldManifests(stage.getContext(), graph);
          break;
        case NONE:
          // do nothing
      }
    }
  }

  private void disableOldManifests(Map parentContext, StageGraphBuilder graph) {
    addStagesForOldManifests(parentContext, graph, DisableManifestStage.PIPELINE_CONFIG_TYPE);
  }

  private void deleteOldManifests(Map parentContext, StageGraphBuilder graph) {
    addStagesForOldManifests(parentContext, graph, DeleteManifestStage.PIPELINE_CONFIG_TYPE);
  }

  private void addStagesForOldManifests(
      Map parentContext, StageGraphBuilder graph, String stageType) {
    List<Map<String, ?>> deployedManifests = getNewManifests(parentContext);
    String account = (String) parentContext.get("account");
    Map manifestMoniker = (Map) parentContext.get("moniker");
    String application = (String) manifestMoniker.get("app");

    deployedManifests.forEach(
        manifest -> {
          Map manifestMetadata = (Map) manifest.get("metadata");
          String manifestName =
              String.format("replicaSet %s", (String) manifestMetadata.get("name"));
          String namespace = (String) manifestMetadata.get("namespace");
          Map annotations = (Map) manifestMetadata.get("annotations");
          String clusterName = (String) annotations.get("moniker.spinnaker.io/cluster");
          String cloudProvider = "kubernetes";

          List<String> previousManifestNames =
              getOldManifestNames(application, account, clusterName, namespace, manifestName);
          previousManifestNames.forEach(
              name -> {
                graph.append(
                    (stage) -> {
                      stage.setType(stageType);
                      Map<String, Object> context = stage.getContext();
                      context.put("account", account);
                      context.put("app", application);
                      context.put("cloudProvider", cloudProvider);
                      context.put("manifestName", name);
                      context.put("location", namespace);
                    });
              });
        });
  }

  private List<Map<String, ?>> getNewManifests(Map parentContext) {
    List<Map<String, ?>> manifests = (List<Map<String, ?>>) parentContext.get("outputs.manifests");
    return manifests.stream()
        .filter(manifest -> manifest.get("kind").equals("ReplicaSet"))
        .collect(Collectors.toList());
  }

  private List<String> getOldManifestNames(
      String application,
      String account,
      String clusterName,
      String namespace,
      String newManifestName) {
    Map cluster =
        oortHelper
            .getCluster(application, account, clusterName, "kubernetes")
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        String.format(
                            "Error fetching cluster %s in account %s and namespace %s",
                            clusterName, account, namespace)));

    List<Map> serverGroups =
        Optional.ofNullable((List<Map>) cluster.get("serverGroups")).orElse(null);

    if (serverGroups == null) {
      return new ArrayList<>();
    }

    return serverGroups.stream()
        .filter(s -> s.get("region").equals(namespace) && !s.get("name").equals(newManifestName))
        .map(s -> (String) s.get("name"))
        .collect(Collectors.toList());
  }
}
