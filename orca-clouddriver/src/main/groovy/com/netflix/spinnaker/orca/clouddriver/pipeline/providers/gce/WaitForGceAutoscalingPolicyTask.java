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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce;

import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WaitForGceAutoscalingPolicyTask implements RetryableTask {
  @Autowired private OortHelper oortHelper;

  @Override
  public long getBackoffPeriod() {
    return 20000;
  }

  @Override
  public long getTimeout() {
    return 1200000;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    StageData data = stage.mapTo(StageData.class);
    String autoscalingMode =
        (String)
            getTargetGroupForLocation(data, data.getRegion()).getAutoscalingPolicy().get("mode");
    return AutoscalingMode.valueOf(autoscalingMode) == data.getMode()
        ? TaskResult.SUCCEEDED
        : TaskResult.RUNNING;
  }

  private TargetServerGroup getTargetGroupForLocation(StageData data, String location) {
    Optional<TargetServerGroup> maybeTargetServerGroup =
        oortHelper.getTargetServerGroup(
            data.getAccountName(), data.getServerGroupName(), location, "gce");

    if (!maybeTargetServerGroup.isPresent()) {
      throw new IllegalStateException(
          String.format(
              "No server group found (serverGroupName: %s:%s)",
              location, data.getServerGroupName()));
    }
    return maybeTargetServerGroup.get();
  }

  enum AutoscalingMode {
    ON,
    OFF,
    ONLY_UP
  }

  @Data
  public static class StageData {
    String accountName;
    String serverGroupName;
    String region;
    AutoscalingMode mode;
  }
}
