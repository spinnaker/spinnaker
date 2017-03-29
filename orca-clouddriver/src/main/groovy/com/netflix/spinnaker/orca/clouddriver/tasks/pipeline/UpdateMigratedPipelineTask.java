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
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.PipelineClusterExtractor;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpdateMigratedPipelineTask extends AbstractCloudProviderAwareTask {

  @Autowired(required = false)
  Front50Service front50Service;

  @Autowired
  List<PipelineClusterExtractor> extractors;

  @Override
  public TaskResult execute(Stage stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Unable to update migrated pipelines, front50 is not enabled. Fix this by setting front50.enabled: true");
    }
    Map<String, Object> context = stage.getContext();
    Map<String, Object> pipeline = (Map<String, Object>) context.get("source.pipeline");

    List<Map> stages = (List<Map>) pipeline.getOrDefault("stages", new ArrayList<>());
    List<Map> katoTasks = (List<Map>) context.get("kato.tasks");
    List<Map> resultObjects = (List<Map>) katoTasks.get(0).get("resultObjects");
    List<Map> replacements = new ArrayList<>(resultObjects.stream().map(o -> (Map) o.get("cluster")).collect(Collectors.toList()));
    stages.forEach(s ->
      PipelineClusterExtractor.getExtractor(s, extractors).ifPresent(e -> e.updateStageClusters(s, replacements))
    );
    String newName = (String) context.getOrDefault("newPipelineName", pipeline.get("name") + " - migrated");
    pipeline.put("name", newName);
    pipeline.remove("id");
    List<Map> triggers = (List<Map>) pipeline.getOrDefault("triggers", new ArrayList<>());
    triggers.forEach(t -> t.put("enabled", false));
    front50Service.savePipeline(pipeline);
    String application = (String) context.get("application");
    Optional<Map<String, Object>> newPipeline = front50Service.getPipelines(application).stream()
      .filter(p -> newName.equals(p.get("name"))).findFirst();
    if (!newPipeline.isPresent()) {
      Map<String, Object> outputs = new HashMap<>();
      outputs.put("exception", "Pipeline migration was successful but could not find new pipeline with name " + newName);
      return new TaskResult(ExecutionStatus.TERMINAL, outputs);
    }
    Map<String, Object> outputs = new HashMap<>();
    outputs.put("newPipelineId", newPipeline.get().get("id"));
    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
