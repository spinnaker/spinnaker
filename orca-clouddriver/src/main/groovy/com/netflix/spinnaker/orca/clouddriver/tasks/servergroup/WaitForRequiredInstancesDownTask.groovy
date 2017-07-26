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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractWaitingForInstancesTask
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class WaitForRequiredInstancesDownTask extends AbstractWaitingForInstancesTask {
  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    def targetDesiredSize = instances.size()

    // During a rolling red/black we want a percentage of instances to be disabled.
    if (stage.context.desiredPercentage != null) {
      Map capacity = (Map) serverGroup.capacity
      Integer percentage = (Integer) stage.context.desiredPercentage
      targetDesiredSize = getDesiredInstanceCount(capacity, percentage)
    }

    // We need at least target instances to be disabled.
    return instances.count { instance ->
      return HealthHelper.someAreDownAndNoneAreUp(instance, interestingHealthProviderNames)
    } >= targetDesiredSize
  }
}
