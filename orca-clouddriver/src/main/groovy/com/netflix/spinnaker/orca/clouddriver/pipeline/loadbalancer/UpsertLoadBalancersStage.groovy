/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.loadbalancer

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancerResultObjectExtrapolationTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancerTask
import com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer.UpsertLoadBalancersTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

/*
 * Pipeline Stage for creating multiple load balancers
 */

@Component
@CompileStatic
class UpsertLoadBalancersStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "upsertLoadBalancers"

  UpsertLoadBalancersStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "upsertLoadBalancers", UpsertLoadBalancersTask)
    def step2 = buildStep(stage, "monitorUpsert", MonitorKatoTask)
    def step3 = buildStep(stage, "extrapolateUpsertResult", UpsertLoadBalancerResultObjectExtrapolationTask)
    def step4 = buildStep(stage, "forceCacheRefresh", UpsertLoadBalancerForceRefreshTask)
    [step1, step2, step3, step4]
  }
}
