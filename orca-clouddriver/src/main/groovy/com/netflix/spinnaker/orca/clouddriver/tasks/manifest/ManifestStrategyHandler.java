/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.netflix.spinnaker.orca.clouddriver.pipeline.manifest.DeleteManifestStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.manifest.DisableManifestStage;
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ManifestStrategyHandler {
  private final OortHelper oortHelper;

  void disableOldManifests(DeployManifestContext parentContext, StageGraphBuilder graph) {
    addStagesForOldManifests(parentContext, graph, DisableManifestStage.PIPELINE_CONFIG_TYPE);
  }

  void deleteOldManifests(DeployManifestContext parentContext, StageGraphBuilder graph) {
    addStagesForOldManifests(parentContext, graph, DeleteManifestStage.PIPELINE_CONFIG_TYPE);
  }

  private Map getNewManifest(DeployManifestContext parentContext) {
    List<Map<String, ?>> manifests = (List<Map<String, ?>>) parentContext.get("outputs.manifests");
    return manifests.get(0);
  }

  private List<String> getOldManifestNames(String application, String account, String clusterName, String namespace, String newManifestName) {
    Map cluster = oortHelper.getCluster(application, account, clusterName, "kubernetes")
      .orElseThrow(() -> new IllegalArgumentException(String.format("Error fetching cluster %s in account %s and namespace %s", clusterName, account, namespace)));

    List<Map> serverGroups = Optional.ofNullable((List<Map>) cluster.get("serverGroups"))
      .orElse(null);

    if (serverGroups == null) {
      return new ArrayList<>();
    }

    return serverGroups.stream()
      .filter(s -> s.get("region").equals(namespace))
      .filter(s -> !s.get("name").equals(newManifestName))
      .map(s -> (String) s.get("name"))
      .collect(Collectors.toList());
  }

  private void addStagesForOldManifests(DeployManifestContext parentContext, StageGraphBuilder graph, String stageType) {
    Map deployedManifest = getNewManifest(parentContext);
    String account = (String) parentContext.get("account");
    Map manifestMoniker = (Map) parentContext.get("moniker");
    String application = (String) manifestMoniker.get("app");

    Map manifestMetadata = (Map) deployedManifest.get("metadata");
    String manifestName = String.format("replicaSet %s", (String) manifestMetadata.get("name"));
    String namespace = (String) manifestMetadata.get("namespace");
    Map annotations = (Map) manifestMetadata.get("annotations");
    String clusterName = (String) annotations.get("moniker.spinnaker.io/cluster");
    String cloudProvider = "kubernetes";

    List<String> previousManifestNames = getOldManifestNames(application, account, clusterName, namespace, manifestName);
    previousManifestNames.forEach(name -> {
      graph.append((stage) -> {
        stage.setType(stageType);
        Map<String, Object> context = stage.getContext();
        context.put("account", account);
        context.put("app", application);
        context.put("cloudProvider", cloudProvider);
        context.put("manifestName", name);
        context.put("location", namespace);
      });
    });
  }
}
