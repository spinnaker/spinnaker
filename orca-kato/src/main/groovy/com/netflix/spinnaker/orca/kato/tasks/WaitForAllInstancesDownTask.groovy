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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class WaitForAllInstancesDownTask extends AbstractWaitingForInstancesTask {

  @Override
  protected boolean hasSucceeded(Stage stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }
    instances.every {
      def healths = interestingHealthProviderNames ? it.health.findAll { health ->
        health.type in interestingHealthProviderNames
      } : it.health
      boolean someAreDown = healths.any { it.state == 'Down' || it.state == 'OutOfService' }
      boolean noneAreUp = !healths.any { it.state == 'Up' }
      someAreDown && noneAreUp
    }
  }
}
