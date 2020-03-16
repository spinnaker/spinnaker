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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.tasks.instance.AbstractWaitingForInstancesTask
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
class WaitForAllNetflixAWSInstancesDownTask extends AbstractWaitingForInstancesTask {
  @Override
  protected boolean hasSucceeded(StageExecution stage, Map serverGroup, List<Map> instances, Collection<String> interestingHealthProviderNames) {
    def oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return true
    }

    def maximumNumberOfHealthProviders = (instances.health*.type).unique().size()
    instances.every {
      def healths = interestingHealthProviderNames ? it.health.findAll { health ->
        health.type in interestingHealthProviderNames
      } : it.health

      if (!interestingHealthProviderNames && !healths) {
        // no health indications (and no specific providers to check), consider instance to be down
        return true
      }

      if (healths.size() == 1 && maximumNumberOfHealthProviders > 1) {
        if (healths[0].type == "Amazon" && healths[0].state == "Unknown" && it.launchTime <= oneHourAgo) {
          // Consider an instance down if it's > 1hr old and only has an Amazon health provider (and there are > 1 configured health providers)
          return true
        }
      }

      boolean someAreDown = healths.any { it.state == 'Down' || it.state == 'OutOfService' }
      boolean noneAreUp = !healths.any { it.state == 'Up' }
      return someAreDown && noneAreUp
    }
  }
}
