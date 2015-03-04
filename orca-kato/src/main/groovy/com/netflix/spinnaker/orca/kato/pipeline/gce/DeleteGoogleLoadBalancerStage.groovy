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

import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.gce.DeleteGoogleLoadBalancerTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DeleteGoogleLoadBalancerStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "deleteLoadBalancer_gce"

  DeleteGoogleLoadBalancerStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "deleteGoogleLoadBalancer", DeleteGoogleLoadBalancerTask)
    // TODO(duftler): Implement DeleteGoogleLoadBalancerForceRefreshTask.
//    def step2 = buildStep("forceCacheRefresh", DeleteGoogleLoadBalancerForceRefreshTask)
    def step3 = buildStep(stage, "monitorDelete", MonitorKatoTask)
    [step1, step3]
  }
}
