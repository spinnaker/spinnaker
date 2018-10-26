/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce.autoscaling;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce.WaitForGceAutoscalingPolicyTask.StageData;

@Slf4j
@Component
public class UpsertGceAutoscalingPolicyTask extends AbstractCloudProviderAwareTask implements Task {
  @Autowired
  private KatoService katoService;

  @Autowired
  private TargetServerGroupResolver resolver;

  public String getType() {
    return "upsertScalingPolicy";
  }

  @Override
  public TaskResult execute(Stage stage) {
    StageData stageData = stage.mapTo(StageData.class);
    TargetServerGroup targetServerGroup;
    if (TargetServerGroup.isDynamicallyBound(stage)) {
      // Dynamically resolved server groups look back at previous stages to find the name
      // of the server group based on *this task's region*
      targetServerGroup = TargetServerGroupResolver.fromPreviousStage(stage);
    } else {
      // Statically resolved server groups should only resolve to a single server group at all times,
      // because each region desired should have been spun off its own ScalingProcess for that region.
      List<TargetServerGroup> resolvedServerGroups = resolver.resolve(stage);
      if (resolvedServerGroups == null || resolvedServerGroups.size() != 1) {
        throw new IllegalStateException(
          String.format("Could not resolve exactly one server group for autoscaling policy upsert for %s in %s",
            stageData.getAccountName(), stageData.getRegion()));
      }

      targetServerGroup = resolvedServerGroups.get(0);
    }

    Map<String, Object> autoscalingPolicy = targetServerGroup.getAutoscalingPolicy();
    String serverGroupName = targetServerGroup.getName();

    Map<String, Object> stageContext = new HashMap<>(stage.getContext());
    stageContext.put("serverGroupName", serverGroupName);

    stageData.setServerGroupName(serverGroupName);

    Map<String, Object> stageOutputs = new HashMap<>();
    stageOutputs.put("notification.type", getType().toLowerCase());
    stageOutputs.put("serverGroupName", serverGroupName);

    Map<String, Map> op = new HashMap<>();
    // NOTE: stage only supports modifying autoscaling policy mode.
    autoscalingPolicy.put("mode", stageData.getMode());
    stageContext.put("autoscalingPolicy", autoscalingPolicy);
    op.put(getType(), stageContext);

    TaskId taskId = katoService.requestOperations(getCloudProvider(stage), Collections.singletonList(op))
      .toBlocking()
      .first();
    stageOutputs.put("kato.last.task.id", taskId);

    return new TaskResult(ExecutionStatus.SUCCEEDED, stageOutputs);
  }
}
