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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.DeleteAmazonLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.DeleteAmazonLoadBalancerTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

/**
 * Created by aglover on 9/26/14.
 */

@Component
@CompileStatic
class DeleteLoadBalancerStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "deleteLoadBalancer"

  DeleteLoadBalancerStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  /**
   * This constructor only exists so we can properly instantiate the deprecated subclass DeleteAmazonLoadBalancerStage.
   * Once that deprecated subclass goes away, this constructor should be removed as well.
   *
   * @deprecated use DeleteLoadBalancerStage() instead.
   */
  @Deprecated
  DeleteLoadBalancerStage(String stageName) {
    super(stageName)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def step1 = buildStep(stage, "deleteAmazonLoadBalancer", DeleteAmazonLoadBalancerTask)
    def step2 = buildStep(stage, "forceCacheRefresh", DeleteAmazonLoadBalancerForceRefreshTask)
    def step3 = buildStep(stage, "monitorDelete", MonitorKatoTask)
    [step1, step2, step3]
  }
}
