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

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.frigga.NameBuilder
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.CanaryConfig
import com.netflix.spinnaker.orca.mine.CanaryDeployment
import com.netflix.spinnaker.orca.mine.Cluster
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.Recipient
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
class RegisterCanaryTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = TimeUnit.SECONDS.toMillis(30)

  @Autowired MineService mineService

  @Override
  TaskResult execute(Stage stage) {
    String app = stage.context.application ?: stage.execution.application
    Canary c = buildCanary(stage)
    Map canary = mineService.registerCanary(app, c)
    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [canary: canary])
  }

  Canary buildCanary(Stage stage) {
    Canary c = new Canary()
    c.owner = new Recipient(name: "Cameron Fieber", email: "cfieber@netflix.com")
    c.canaryConfig = stage.mapTo('/canaryConfig', CanaryConfig)
    for (canary in stage.context.canaries) {
      def nameBuilder = new CanaryPairClusterNameBuilder(canary.application, canary.stack, canary.freeFormDetails)
      c.canaryDeployments << new CanaryDeployment(
        canaryCluster: new Cluster(accountName: canary.credentials, region: canary.region, name: nameBuilder.canaryCluster),
        baselineCluster: new Cluster(accountName: canary.credentials, region: canary.region, name: nameBuilder.baselineCluster))
    }
    return c
  }

  class CanaryPairClusterNameBuilder extends NameBuilder {
    String app
    String stack
    String freeFormDetails

    CanaryPairClusterNameBuilder(String app, String stack, String freeFormDetails) {
      this.app = app
      this.stack = stack
      this.freeFormDetails = freeFormDetails
    }

    String getCanaryCluster() {
      super.combineAppStackDetail(app, stack, detailsWithSuffix("canary"))
    }

    String getBaselineCluster() {
      super.combineAppStackDetail(app, stack, detailsWithSuffix("baseline"))
    }

    String detailsWithSuffix(String suffix) {
      if (freeFormDetails == null) {
        return suffix
      }
      return freeFormDetails + "_${suffix}"
    }
  }
}
