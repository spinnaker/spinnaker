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

package com.netflix.spinnaker.orca.mine.pipeline;

import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.PipelineClusterExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CanaryPipelineClusterExtractor implements PipelineClusterExtractor {
  @Override
  public String getStageType() {
    return CanaryStage.PIPELINE_CONFIG_TYPE;
  }

  @Override
  public void updateStageClusters(Map stage, List<Map> replacements) {
    List<Map> clusterPairs = (List<Map>) stage.get("clusterPairs");
    clusterPairs.forEach(
        pair -> {
          pair.put("baseline", replacements.remove(0));
          pair.put("canary", replacements.remove(0));
        });
  }

  @Override
  public List<Map> extractClusters(Map stage) {
    List<Map> results = new ArrayList<>();
    List<Map> clusterPairs = (List<Map>) stage.getOrDefault("clusterPairs", new ArrayList<>());
    clusterPairs.forEach(
        pair -> {
          results.add((Map) pair.get("baseline"));
          results.add((Map) pair.get("canary"));
        });
    return results;
  }
}
