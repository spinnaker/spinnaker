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

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.kato.pipeline.strategy.DeployStrategyStage
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.CreateCopyLastAsgTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class CopyLastAsgStage extends DeployStrategyStage {

  public static final String MAYO_CONFIG_TYPE = "copyLastAsg"

  CopyLastAsgStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  List<Step> basicSteps(Stage stage) {
    def step1 = buildStep(stage, "createCopyLastAsg", CreateCopyLastAsgTask)
    def step2 = buildStep(stage, "monitorDeploy", MonitorKatoTask)
    def step3 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    def step4 = buildStep(stage, "waitForUpInstances", WaitForUpInstancesTask)
    def step5 = buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    [step1, step2, step3, step4, step5]
  }

  @Override
  protected CleanupConfig determineClusterForCleanup(Stage stage) {
    // if may be copying from a different region (OR EVEN CLUSTER!), so use the provided "source" map instead of the
    // target configuration.
    def source = stage.mapTo("/source", Source)
    return new CleanupConfig(source.account, Names.parseName(source.asgName).cluster, [source.region])
  }

  static class Source {
    String account
    String asgName
    String region
  }
}
