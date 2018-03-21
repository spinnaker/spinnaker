/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster;

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.RollbackServerGroupStage;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.DetermineRollbackCandidatesTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RollbackClusterStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "rollbackCluster";

  private final RollbackServerGroupStage rollbackServerGroupStage;

  private final WaitStage waitStage;

  @Autowired
  public RollbackClusterStage(RollbackServerGroupStage rollbackServerGroupStage,
                              WaitStage waitStage) {
    this.rollbackServerGroupStage = rollbackServerGroupStage;
    this.waitStage = waitStage;
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder
      .withTask("determineRollbackCandidates", DetermineRollbackCandidatesTask.class);
  }

  @Override
  public void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    StageData stageData = parent.mapTo(StageData.class);

    Map<String, Object> parentOutputs = parent.getOutputs();
    Map<String, String> rollbackTypes = (Map<String, String>) parentOutputs.get("rollbackTypes");
    Map<String, Map<String, Object>> rollbackContexts = (Map<String, Map<String, Object>>) parentOutputs.get("rollbackContexts");

    // filter out any regions that do _not_ have a rollback target
    List<String> regionsToRollback = stageData.regions
      .stream()
      .filter(rollbackTypes::containsKey)
      .collect(Collectors.toList());

    for (String region : regionsToRollback) {
      Map<String, Object> context = new HashMap<>();
      context.put(
        "rollbackType",
        ((Map) parent.getOutputs().get("rollbackTypes")).get(region)
      );

      Map<String, Object> rollbackContext = rollbackContexts.get(region);
      if (stageData.additionalRollbackContext != null) {
        rollbackContext.putAll(stageData.additionalRollbackContext);
      }

      context.put("rollbackContext", rollbackContext);
      context.put("type", rollbackServerGroupStage.getType());
      context.put("region", region);
      context.put("credentials", stageData.credentials);
      context.put("cloudProvider", stageData.cloudProvider);

      // propagate any attributes of the parent stage that are relevant to this rollback
      context.putAll(propagateParentStageContext(parent.getParent()));

      Stage rollbackStage = graph.add((it) -> {
        it.setType(rollbackServerGroupStage.getType());
        it.setName("Rollback " + region);
        it.setContext(context);
      });

      if (stageData.waitTimeBetweenRegions != null && regionsToRollback.indexOf(region) < regionsToRollback.size() - 1) {
        // only add the waitStage if we're not the very last region!
        graph.connect(
          rollbackStage,
          (it) -> {
            it.setType(waitStage.getType());
            it.setName("Wait after " + region);
            it.setContext(Collections.singletonMap("waitTime", stageData.waitTimeBetweenRegions));
          });
      }
    }
  }

  private static Map<String, Object> propagateParentStageContext(Stage parent) {
    Map<String, Object> contextToPropagate = new HashMap<>();

    if (parent == null) {
      return contextToPropagate;
    }

    Map<String, Object> parentStageContext = parent.getContext();
    if (parentStageContext.containsKey("interestingHealthProviderNames")) {
      // propagate any health overrides that have been set on a parent stage (ie. rollback on failed deploy)
      contextToPropagate.put("interestingHealthProviderNames", parentStageContext.get("interestingHealthProviderNames"));
    }

    if (parentStageContext.containsKey("sourceServerGroupCapacitySnapshot")) {
      // in the case of rolling back a failed deploy, this is the capacity that should be restored!
      // ('sourceServerGroupCapacitySnapshot' represents the original capacity before any pinning has occurred)
      contextToPropagate.put("sourceServerGroupCapacitySnapshot", parentStageContext.get("sourceServerGroupCapacitySnapshot"));
    }

    return contextToPropagate;
  }

  static class StageData {
    public String credentials;
    public String cloudProvider;

    public List<String> regions;
    public Long waitTimeBetweenRegions;

    public Map<String, Object> additionalRollbackContext;
  }
}
