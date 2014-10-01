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

import com.netflix.spinnaker.orca.kato.tasks.*
import com.netflix.spinnaker.orca.kato.tasks.gce.*
import com.netflix.spinnaker.orca.pipeline.LinearStage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DestroyGoogleReplicaPoolStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "destroyAsg_gce"

  @Autowired ResizeGoogleReplicaPoolStage resizeGoogleReplicaPoolStage

  DestroyGoogleReplicaPoolStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps() {
    def resizeSteps = resizeGoogleReplicaPoolStage.buildSteps()

    def step1 = steps.get("PreconfigureResizeStep")
                     .tasklet(buildTask(PreconfigureDestroyGoogleReplicaPoolTask))
                     .build()
    def step2 = steps.get("DestroyAsgStep")
                     .tasklet(buildTask(DestroyGoogleReplicaPoolTask))
                     .build()
    def step3 = steps.get("MonitorAsgStep")
                     .tasklet(buildTask(MonitorKatoTask))
                     .build()
    def step4 = steps.get("ForceCacheRefreshStep")
                     .tasklet(buildTask(ServerGroupCacheForceRefreshTask))
                     .build()
    def step5 = steps.get("WaitForCapacityMatchStep")
                     .tasklet(buildTask(WaitForCapacityMatchTask))
                     .build()
    def step6 = steps.get("NotifyEchoStep")
                     .tasklet(buildTask(NotifyEchoTask))
                     .build()

    [step1, resizeSteps, step2, step3, step4, step5, step6].flatten().toList()
  }
}
