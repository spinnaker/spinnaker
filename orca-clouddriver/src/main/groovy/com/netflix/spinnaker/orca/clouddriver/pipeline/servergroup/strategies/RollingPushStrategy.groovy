/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import groovy.transform.Immutable
import com.netflix.spinnaker.orca.kato.pipeline.ModifyAsgLaunchConfigurationStage
import com.netflix.spinnaker.orca.kato.pipeline.RollingPushStage
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage

@Component
@Deprecated
class RollingPushStrategy implements Strategy {

  final String name = "rollingpush"

  @Autowired
  ModifyAsgLaunchConfigurationStage modifyAsgLaunchConfigurationStage

  @Autowired
  RollingPushStage rollingPushStage

  @Autowired
  SourceResolver sourceResolver

  @Override
  <T extends Execution<T>> List<Stage<T>> composeFlow(Stage<T> stage) {
    def stages = []
    def source = sourceResolver.getSource(stage)

    def modifyCtx = stage.context + [
        region: source.region,
        regions: [source.region],
        asgName: source.asgName,
        'deploy.server.groups': [(source.region): [source.asgName]],
        useSourceCapacity: true,
        credentials: source.account,
        source: [
            asgName: source.asgName,
            account: source.account,
            region: source.region,
            useSourceCapacity: true
        ]
    ]

    stages << newStage(
      stage.execution,
      modifyAsgLaunchConfigurationStage.type,
      "modifyLaunchConfiguration",
      modifyCtx,
      stage,
      SyntheticStageOwner.STAGE_AFTER
    )

    def terminationConfig = stage.mapTo("/termination", TerminationConfig)
    if (terminationConfig.relaunchAllInstances || terminationConfig.totalRelaunches > 0) {
      stages << newStage(
        stage.execution,
        rollingPushStage.type,
        "rollingPush",
        modifyCtx,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    }

    return stages
  }

  @Override
  boolean replacesBasicSteps() {
    return true
  }

  @Immutable
  static class TerminationConfig {
    String order
    boolean relaunchAllInstances
    int concurrentRelaunches
    int totalRelaunches
  }
}
