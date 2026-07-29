/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractWarmPoolTask implements CloudProviderAware, Task {

  @Autowired KatoService katoService;

  @Autowired TargetServerGroupResolver resolver;

  public abstract String getType();

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    TargetServerGroup targetServerGroup;
    if (TargetServerGroup.isDynamicallyBound(stage)) {
      targetServerGroup = TargetServerGroupResolver.fromPreviousStage(stage);
    } else {
      targetServerGroup = resolver.resolve(stage).get(0);
    }
    String asgName = targetServerGroup.getName();
    String region = targetServerGroup.getRegion();

    Map<String, Object> stageContext = new HashMap<>(stage.getContext());
    stageContext.put(
        "asgs",
        Collections.singletonList(
            Map.of("serverGroupName", asgName, "region", region)));

    StageData stageData = stage.mapTo(StageData.class);
    stageData.setAsgName(asgName);

    TaskId taskId =
        katoService.requestOperations(
            getCloudProvider(stage),
            Collections.singletonList(Collections.singletonMap(getType(), stageContext)));

    Map<String, Object> stageOutputs = new HashMap<>();
    stageOutputs.put("notification.type", getType().toLowerCase());
    stageOutputs.put("deploy.server.groups", stageData.getAffectedServerGroupMap());
    stageOutputs.put("asgName", asgName);
    stageOutputs.put("kato.last.task.id", taskId);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stageOutputs).build();
  }

  @Data
  public static class StageData {
    private String region;
    private String asgName;

    public Map<String, List<String>> getAffectedServerGroupMap() {
      return Collections.singletonMap(region, Collections.singletonList(asgName));
    }
  }
}
