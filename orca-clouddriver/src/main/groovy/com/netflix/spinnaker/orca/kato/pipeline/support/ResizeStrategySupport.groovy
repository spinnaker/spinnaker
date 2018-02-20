/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.RollingRedBlackStageData
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public class ResizeStrategySupport {
  static ResizeStrategy.Source getSource(TargetServerGroupResolver targetServerGroupResolver,
                                         StageData stageData,
                                         Map baseContext) {
    if (stageData.source) {
      return new ResizeStrategy.Source(
        region: stageData.source.region,
        serverGroupName: stageData.source.serverGroupName ?: stageData.source.asgName,
        credentials: stageData.credentials ?: stageData.account,
        cloudProvider: stageData.cloudProvider
      )
    }

    // no source server group specified, lookup current server group
    TargetServerGroup target = targetServerGroupResolver.resolve(
      new Stage(null, null, null, baseContext + [target: TargetServerGroup.Params.Target.current_asg_dynamic])
    )?.get(0)

    if (!target) {
      throw new IllegalStateException("No target server groups found (${baseContext})")
    }

    return new ResizeStrategy.Source(
      region: target.getLocation().value,
      serverGroupName: target.getName(),
      credentials: stageData.credentials ?: stageData.account,
      cloudProvider: stageData.cloudProvider
    )
  }
}
