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
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractWaitForClusterWideClouddriverTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.ShrinkClusterTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.WaitForClusterShrinkTask;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShrinkClusterStage extends AbstractClusterWideClouddriverOperationStage {

  public static final String STAGE_TYPE = "shrinkCluster";

  private final DisableClusterStage disableClusterStage;

  @Autowired
  public ShrinkClusterStage(
      DynamicConfigService dynamicConfigService, DisableClusterStage disableClusterStage) {
    super(dynamicConfigService);
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
  public void addAdditionalBeforeStages(
      @Nonnull StageExecution parent, @Nonnull StageGraphBuilder graph) {
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

      // this flag controls if the "disableCluster" step needs to be added to the ShrinkCluster
      // stage or not.
      // This is to allow a user the option to bypass disabling the cluster before shrinking it.
      // Note that the user has
      // to explicitly opt-in by setting the below-mentioned property in the context to bypass it.
      boolean runDisableClusterStep = true;
      try {
        runDisableClusterStep =
            (boolean) parent.getContext().getOrDefault("runDisableClusterStep", true);
      } catch (Exception e) {
        log.error(
            "error reading 'runDisableClusterStep' property from the stage manifest. "
                + "DisableCluster stage will be added to the {} stage",
            STAGE_TYPE);
      }

      if (runDisableClusterStep) {
        graph.add(
            (it) -> {
              it.setType(disableClusterStage.getType());
              it.setName("disableCluster");
              it.setContext(context);
            });
      } else {
        log.info(
            "not adding 'disableCluster' step to the {} stage as it has been disabled", STAGE_TYPE);
      }
    }
  }
}
