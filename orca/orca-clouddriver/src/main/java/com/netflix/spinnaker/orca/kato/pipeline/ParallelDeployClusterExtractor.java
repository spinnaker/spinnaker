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

package com.netflix.spinnaker.orca.kato.pipeline;

import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.PipelineClusterExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ParallelDeployClusterExtractor implements PipelineClusterExtractor {

  @Override
  public String getStageType() {
    return ParallelDeployStage.PIPELINE_CONFIG_TYPE;
  }

  @Override
  public void updateStageClusters(Map stage, List<Map> replacements) {
    List<Map> clusters = (List<Map>) stage.get("clusters");
    stage.put("clusters", new ArrayList<>(replacements.subList(0, clusters.size())));
    replacements.removeAll((List<Map>) stage.get("clusters"));
  }

  @Override
  public List<Map> extractClusters(Map stage) {
    return (List<Map>) stage.getOrDefault("clusters", new ArrayList<Map>());
  }
}
