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

import org.springframework.stereotype.Component

@Component
class WaitForUpInstancesTask extends AbstractWaitingForInstancesTask {

  @Override
  protected boolean hasSucceeded(Map asg, List instances, Collection<String> interestingHealthProviderNames) {
    if (asg.minSize > instances.size()) {
      return false
    }

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    int healthyCount = instances.count {
      def healths = interestingHealthProviderNames ? it.health.findAll { health ->
        health.type in interestingHealthProviderNames
      } : it.health
      boolean someAreUp = healths.any { it.state == 'Up' }
      boolean noneAreDown = !healths.any { it.state == 'Down' }
      someAreUp && noneAreDown
    }

    return healthyCount >= asg.minSize
  }

}
