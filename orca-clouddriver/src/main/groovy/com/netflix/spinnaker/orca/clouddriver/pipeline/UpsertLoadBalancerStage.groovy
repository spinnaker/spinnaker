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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.UpsertLoadBalancerResultObjectExtrapolationTask
import com.netflix.spinnaker.orca.clouddriver.tasks.UpsertLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.UpsertLoadBalancerTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class UpsertLoadBalancerStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "upsertLoadBalancer"

  UpsertLoadBalancerStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  /**
   * This constructor only exists so we can properly instantiate the deprecated subclass UpsertAmazonLoadBalancerStage.
   * Once that deprecated subclass goes away, this constructor should be removed as well.
   *
   * @deprecated use UpsertLoadBalancerStage() instead.
   */
  @Deprecated
  UpsertLoadBalancerStage(String stageName) {
    super(stageName)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "upsertLoadBalancer", UpsertLoadBalancerTask)
    def step2 = buildStep(stage, "monitorUpsert", MonitorKatoTask)
    def step3 = buildStep(stage, "extrapolateUpsertResult", UpsertLoadBalancerResultObjectExtrapolationTask)
    def step4 = buildStep(stage, "forceCacheRefresh", UpsertLoadBalancerForceRefreshTask)
    [step1, step2, step3, step4]
  }
}
