/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.model.Instance;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstancesCheckTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitForUpInstancesTask;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WaitForCapacityMatchTask extends AbstractInstancesCheckTask {

  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return (Map<String, List<String>>) stage.getContext().get("deploy.server.groups");
  }

  @Override
  protected Map<String, Object> getAdditionalRunningStageContext(
      StageExecution stage, ServerGroup serverGroup) {
    Map<String, Object> result = new HashMap<>();
    boolean disabled = Boolean.TRUE.equals(serverGroup.getDisabled());
    if (!disabled) {
      result.put(
          "targetDesiredSize",
          WaitForUpInstancesTask.calculateTargetDesiredSize(stage, serverGroup));
    }
    return result;
  }

  @Override
  protected boolean hasSucceeded(
      StageExecution stage,
      ServerGroup serverGroup,
      List<Instance> instances,
      Collection<String> interestingHealthProviderNames) {
    WaitForUpInstancesTask.Splainer splainer =
        new WaitForUpInstancesTask.Splainer()
            .add(
                String.format(
                    "Capacity match check for server group %s [executionId=%s, stagedId=%s]",
                    serverGroup.getName(),
                    stage.getExecution().getId(),
                    stage.getExecution().getId()));

    try {
      Map<String, Object> context = stage.getContext();

      ServerGroup.Capacity capacity = serverGroup.getCapacity();
      if (capacity == null || capacity.getDesired() == null) {
        splainer.add(
            "short-circuiting out of WaitForCapacityMatchTask because of empty capacity in serverGroup="
                + serverGroup);
        return false;
      }

      Integer desired;

      if (WaitForUpInstancesTask.useConfiguredCapacity(stage, capacity)) {
        desired =
            Optional.ofNullable((Map<String, Object>) context.get("capacity"))
                .map(it -> it.get("desired"))
                .map(
                    value ->
                        value instanceof Number
                            ? ((Number) value).intValue()
                            : Integer.parseInt(value.toString()))
                .orElse(null);
        splainer.add(String.format("using desired from stage.context.capacity (%s)", desired));
      } else {
        desired = capacity.getDesired();
      }

      Integer targetDesiredSize =
          Optional.ofNullable((Number) context.get("targetDesiredSize"))
              .map(Number::intValue)
              .orElse(null);

      splainer.add(
          String.format(
              "checking if capacity matches (desired=%s, target=%s current=%s)",
              desired, targetDesiredSize == null ? "none" : targetDesiredSize, instances.size()));
      if (targetDesiredSize != null && targetDesiredSize != 0) {
        // `targetDesiredSize` is derived from `targetHealthyDeployPercentage` and if present,
        // then scaling has succeeded if the number of instances is greater than this value.
        if (instances.size() < targetDesiredSize) {
          splainer.add(
              "short-circuiting out of WaitForCapacityMatchTask because targetDesired and current capacity don't match");
          return false;
        }
      } else if (desired == null || desired != instances.size()) {
        splainer.add(
            "short-circuiting out of WaitForCapacityMatchTask because expected and current capacity don't match");
        return false;
      }

      boolean disabled = Boolean.TRUE.equals(serverGroup.getDisabled());

      if (disabled) {
        splainer.add(
            "capacity matches but server group is disabled, so returning hasSucceeded=true");
        return true;
      }

      splainer.add(
          "capacity matches and server group is enabled, so we delegate to WaitForUpInstancesTask to check for healthy instances");
      return WaitForUpInstancesTask.allInstancesMatch(
          stage, serverGroup, instances, interestingHealthProviderNames, splainer);
    } finally {
      splainer.splain();
    }
  }
}
