/*
 * Copyright 2016 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.pipeline;

import java.util.*;
import java.util.stream.Collectors;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.PipelineClusterExtractor;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MigratePipelineClustersTask extends AbstractCloudProviderAwareTask {

  @Autowired
  KatoService katoService;

  @Autowired(required = false)
  Front50Service front50Service;

  @Autowired
  List<PipelineClusterExtractor> extractors;

  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Cannot migrate pipeline clusters, front50 is not enabled. Fix this by setting front50.enabled: true");
    }

    Map<String, Object> context = stage.getContext();
    Optional<Map<String, Object>> pipelineMatch = getPipeline(context);

    if (!pipelineMatch.isPresent()) {
      return pipelineNotFound(context);
    }

    List<Map> sources = getSources(pipelineMatch.get());
    List<Map<String, Map>> operations = generateKatoOperation(context, sources);

    TaskId taskId = katoService.requestOperations(getCloudProvider(stage), operations)
      .toBlocking()
      .first();
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "migratepipelineclusters");
    outputs.put("kato.last.task.id", taskId);
    outputs.put("source.pipeline", pipelineMatch.get());
    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }

  private List<Map> getSources(Map<String, Object> pipeline) {
    List<Map> stages = (List<Map>) pipeline.getOrDefault("stages", new ArrayList<>());
    return stages.stream().map(s -> {
      Optional<PipelineClusterExtractor> extractor = PipelineClusterExtractor.getExtractor(s, extractors);
      if (extractor.isPresent()) {
        return extractor.get().extractClusters(s).stream()
          .map(c -> Collections.singletonMap("cluster", c))
          .collect(Collectors.toList());
      }
      return new ArrayList<Map>();
    }).flatMap(Collection::stream).collect(Collectors.toList());
  }

  private List<Map<String, Map>> generateKatoOperation(Map<String, Object> context, List<Map> sources) {
    Map<String, Object> migrateOperation = new HashMap<>();
    migrateOperation.put("sources", sources);
    Map<String, Map> operation = new HashMap<>();
    operation.put("migrateClusterConfigurations", migrateOperation);
    addMappings(context, migrateOperation);

    List<Map<String, Map>> operations = new ArrayList<>();
    operations.add(operation);
    return operations;
  }

  private void addMappings(Map<String, Object> context, Map<String, Object> operation) {
    operation.put("regionMapping", context.getOrDefault("regionMapping", new HashMap<>()));
    operation.put("accountMapping", context.getOrDefault("accountMapping", new HashMap<>()));
    operation.put("subnetTypeMapping", context.getOrDefault("subnetTypeMapping", new HashMap<>()));
    operation.put("elbSubnetTypeMapping", context.getOrDefault("elbSubnetTypeMapping", new HashMap<>()));
    operation.put("iamRoleMapping", context.getOrDefault("iamRoleMapping", new HashMap<>()));
    operation.put("keyPairMapping", context.getOrDefault("keyPairMapping", new HashMap<>()));
    operation.put("dryRun", context.getOrDefault("dryRun", false));
    operation.put("allowIngressFromClassic", context.getOrDefault("allowIngressFromClassic", false));
  }

  private Optional<Map<String, Object>> getPipeline(Map<String, Object> context) {
    String application = (String) context.get("application");
    String pipelineId = (String) context.get("pipelineConfigId");
    return front50Service.getPipelines(application).stream()
      .filter(p -> pipelineId.equals(p.get("id"))).findFirst();
  }

  private TaskResult pipelineNotFound(Map<String, Object> context) {
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("exception", "Could not find pipeline with ID " + context.get("pipelineConfigId"));
    return new TaskResult(ExecutionStatus.TERMINAL, outputs);
  }

}
