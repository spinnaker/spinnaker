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
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceNotFoundException
import com.netflix.spinnaker.orca.kato.tasks.DestroyAsgTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForDestroyedAsgTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DestroyAsgStage extends TargetReferenceLinearStageSupport {
  static final String DESTROY_ASG_DESCRIPTIONS_KEY = "destroyAsgDescriptions"
  static final String PIPELINE_CONFIG_TYPE = "destroyAsg"

  DestroyAsgStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    try {
      composeTargets(stage)
      def step1 = buildStep(stage, "destroyAsg", DestroyAsgTask)
      def step2 = buildStep(stage, "monitorAsg", MonitorKatoTask)
      def step3 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
      def step4 = buildStep(stage, "waitForDestroyedAsg", WaitForDestroyedAsgTask)
      [step1, step2, step3, step4].flatten().toList()
    } catch (TargetReferenceNotFoundException ignored) {
      [buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)].flatten().toList()
    }
  }
}
