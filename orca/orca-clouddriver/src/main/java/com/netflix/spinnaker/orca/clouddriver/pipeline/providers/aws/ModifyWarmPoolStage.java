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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.ForceCacheRefreshAware;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool.DeleteWarmPoolTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool.UpsertWarmPoolTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModifyWarmPoolStage extends TargetServerGroupLinearStageSupport
    implements ForceCacheRefreshAware {

  public static final String TYPE = StageDefinitionBuilder.getType(ModifyWarmPoolStage.class);

  private final DynamicConfigService dynamicConfigService;

  @Autowired
  public ModifyWarmPoolStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {
    StageData data = stage.mapTo(StageData.class);
    switch (data.getAction()) {
      case upsert:
        builder.withTask("upsertWarmPool", UpsertWarmPoolTask.class);
        break;
      case delete:
        builder.withTask("deleteWarmPool", DeleteWarmPoolTask.class);
        break;
      default:
        throw new RuntimeException("No action specified!");
    }

    builder.withTask("monitor", MonitorKatoTask.class);

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
    }

    builder.withTask("waitForWarmPool", WaitForWarmPool.class);
  }

  public enum StageAction {
    upsert,
    delete
  }

  @Data
  public static class StageData {
    StageAction action;
  }

  @Component
  public static class WaitForWarmPool implements RetryableTask {

    private final CloudDriverService cloudDriverService;

    @Autowired
    public WaitForWarmPool(CloudDriverService cloudDriverService) {
      this.cloudDriverService = cloudDriverService;
    }

    @Override
    public long getTimeout() {
      return TimeUnit.MINUTES.toMillis(20);
    }

    @Override
    public long getBackoffPeriod() {
      return TimeUnit.SECONDS.toMillis(20);
    }

    @Nonnull
    @Override
    public TaskResult execute(@Nonnull StageExecution stage) {
      WaitStageData stageData = stage.mapTo(WaitStageData.class);
      Optional<Map<String, Object>> serverGroup =
          cloudDriverService.getTargetServerGroupAsMap(
              stageData.getCredentials(), stageData.getServerGroupName(), stageData.getRegion());

      if (!serverGroup.isPresent()) {
        throw new IllegalStateException(
            "No server group found (serverGroupName: "
                + stageData.getRegion()
                + ":"
                + stageData.getServerGroupName()
                + ")");
      }

      Map<?, ?> asg = (Map<?, ?>) serverGroup.get().get("asg");
      Object warmPoolConfiguration = asg != null ? asg.get("warmPoolConfiguration") : null;
      boolean isComplete =
          stageData.isDelete() ? warmPoolConfiguration == null : warmPoolConfiguration != null;

      return isComplete
          ? TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
          : TaskResult.ofStatus(ExecutionStatus.RUNNING);
    }

    @Data
    public static class WaitStageData {
      private String credentials;
      private String serverGroupName;
      private String asgName;
      private List<String> regions;
      private String region;
      private String action;

      public String getRegion() {
        return (regions != null && !regions.isEmpty()) ? regions.get(0) : region;
      }

      public String getServerGroupName() {
        return serverGroupName != null ? serverGroupName : asgName;
      }

      public boolean isDelete() {
        return StageAction.delete.toString().equals(action);
      }
    }
  }
}
