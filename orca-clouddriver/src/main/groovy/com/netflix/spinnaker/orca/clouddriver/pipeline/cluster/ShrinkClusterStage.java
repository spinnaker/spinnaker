/*
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask;
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard;
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {

  public static final String STAGE_TYPE = "shrinkCluster";

  private final DisableClusterStage disableClusterStage;

  @Autowired
  public ShrinkClusterStage(
      TrafficGuard trafficGuard,
      LockingConfigurationProperties lockingConfigurationProperties,
      DynamicConfigService dynamicConfigService,
      DisableClusterStage disableClusterStage) {
    super(trafficGuard, lockingConfigurationProperties, dynamicConfigService);
    this.disableClusterStage = disableClusterStage;
  }

  @Override
  protected Class<? extends AbstractClusterWideClouddriverTask> getClusterOperationTask() {
    return ShrinkClusterTask.class;
  }

  @Override
  protected Class<? extends AbstractWaitForClusterWideClouddriverTask> getWaitForTask() {
    return WaitForClusterShrinkTask.class;
  }

  @Override
  public void addAdditionalBeforeStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    if (Objects.equals(parent.getContext().get("allowDeleteActive"), true)) {
      Map<String, Object> context = new HashMap<>(parent.getContext());
      context.put("remainingEnabledServerGroups", parent.getContext().get("shrinkToSize"));
      context.put("preferLargerOverNewer", parent.getContext().get("retainLargerOverNewer"));
      context.put(
          "continueIfClusterNotFound", Objects.equals(parent.getContext().get("shrinkToSize"), 0));

      // We don't want the key propagated if interestingHealthProviderNames isn't defined, since
      // this prevents
      // health providers from the stage's 'determineHealthProviders' task to be added to the
      // context.
      if (parent.getContext().get("interestingHealthProviderNames") != null) {
        context.put(
            "interestingHealthProviderNames",
            parent.getContext().get("interestingHealthProviderNames"));
      }

      graph.add(
          (it) -> {
            it.setType(disableClusterStage.getType());
            it.setName("disableCluster");
            it.setContext(context);
          });
    }
  }
}
