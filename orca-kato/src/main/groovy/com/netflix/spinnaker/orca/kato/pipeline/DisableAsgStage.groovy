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

import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceLinearStageSupport
import com.netflix.spinnaker.orca.kato.tasks.*
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DisableAsgStage extends TargetReferenceLinearStageSupport {
  static final String MAYO_CONFIG_TYPE = "disableAsg"

  DisableAsgStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    composeTargets(stage)

    def step1 = buildStep(stage, "disableAsg", DisableAsgTask)
    def step2 = buildStep(stage, "monitorAsg", MonitorKatoTask)
    def step3 = buildStep(stage, "waitForDownInstances", WaitForAllInstancesDownTask)
    def step4 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    [step1, step2, step3, step4]
  }

}
