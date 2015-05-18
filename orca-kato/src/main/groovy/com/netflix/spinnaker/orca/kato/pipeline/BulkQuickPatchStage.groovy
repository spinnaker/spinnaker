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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForDownInstanceHealthTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForUpInstanceHealthTask
import com.netflix.spinnaker.orca.kato.tasks.quip.InstanceHealthCheckTask
import com.netflix.spinnaker.orca.kato.tasks.quip.MonitorQuipTask
import com.netflix.spinnaker.orca.kato.tasks.quip.TriggerQuipTask
import com.netflix.spinnaker.orca.kato.tasks.quip.VerifyQuipTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class BulkQuickPatchStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "bulkQuickPatch"

  BulkQuickPatchStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "verifyQuipIsRunning", VerifyQuipTask)
    def step2 = buildStep(stage, "triggerQuip", TriggerQuipTask)
    def step3 = buildStep(stage, "monitorQuip", MonitorQuipTask)
    def step4 = buildStep(stage, "instanceHealthCheck", InstanceHealthCheckTask)
    def step5 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    def step6 = buildStep(stage, "waitForDiscoveryState", WaitForUpInstanceHealthTask)
    [step1, step2, step3, step4, step5, step6]
  }
}
