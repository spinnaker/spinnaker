/*
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstancesCheckTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.WaitingForInstancesTaskHelper;
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@Deprecated
/**
 * @deprecated this does not handle some corner cases (like the platformHealthOnly flag), use {@link
 *     WaitForRequiredInstancesDownTask} instead.
 */
public class WaitForAllInstancesNotUpTask extends AbstractInstancesCheckTask {
  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return WaitingForInstancesTaskHelper.extractServerGroups(stage);
  }

  @Override
  protected boolean hasSucceeded(
      StageExecution stage,
      Map<String, Object> serverGroup,
      List<Map<String, Object>> instances,
      Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true;
    }

    return instances.stream()
        .allMatch(
            instance -> {
              List<Map<String, Object>> healths =
                  HealthHelper.filterHealths(instance, interestingHealthProviderNames);

              boolean noneAreUp =
                  healths == null // No health indications (and no specific providers to check),
                      // consider instance to be down.
                      || healths.stream().noneMatch(health -> "Up".equals(health.get("state")));
              return noneAreUp;
            });
  }
}
