/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.gce

import com.netflix.spinnaker.orca.kato.tasks.NotifyEchoTask
import com.netflix.spinnaker.orca.kato.tasks.gce.ResizeGoogleReplicaPoolTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ResizeGoogleReplicaPoolStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "resizeAsg_gce"

  ResizeGoogleReplicaPoolStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("ResizeAsgStep")
                     .tasklet(buildTask(ResizeGoogleReplicaPoolTask))
                     .build()
    def step2 = steps.get("MonitorAsgStep")
                     .tasklet(buildTask(MonitorKatoTask))
                     .build()
    def step3 = steps.get("ForceCacheRefreshStep")
                     .tasklet(buildTask(ServerGroupCacheForceRefreshTask))
                     .build()
    def step4 = steps.get("WaitForCapacityMatchStep")
                     .tasklet(buildTask(WaitForCapacityMatchTask))
                     .build()
    def step5 = steps.get("SendNotificationStep")
                     .tasklet(buildTask(NotifyEchoTask))
                     .build()

    [step1, step2, step3, step4, step5]
  }
}
