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


package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractInstancesCheckTask
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractWaitingForInstancesTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
@Deprecated
/**
 * @deprecated this does not handle some corner cases (like the platformHealthOnly flag), use {@link WaitForRequiredInstancesDownTask} instead
 */
class WaitForAllInstancesNotUpTask extends AbstractInstancesCheckTask {
  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return AbstractWaitingForInstancesTask.extractServerGroups(stage)
  }

  @Override
  protected boolean hasSucceeded(StageExecution stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    instances.every { instance ->
      List<Map> healths = HealthHelper.filterHealths(instance, interestingHealthProviderNames)

      if (!interestingHealthProviderNames && !healths) {
        // No health indications (and no specific providers to check), consider instance to be down.
        return true
      }

      boolean noneAreUp = !healths.any { it.state == 'Up' }
      return noneAreUp
    }
  }
}

