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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.RollingRedBlackStageData
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.Capacity
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResizeStrategySupport {

  @Autowired
  OortHelper oortHelper

  @Autowired
  Registry registry

  static ResizeStrategy.Source getSource(TargetServerGroupResolver targetServerGroupResolver,
                                         StageData stageData,
                                         Map baseContext) {
    if (StageData.EMPTY_SOURCE.equals(stageData.source)) {
      return null;
    }

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

  public ResizeStrategy.Capacity getCapacity(String account,
                                             String serverGroupName,
                                             String cloudProvider,
                                             String location) {
    TargetServerGroup tsg = oortHelper.getTargetServerGroup(account, serverGroupName, location, cloudProvider)
      .orElseThrow({
      new IllegalStateException("no server group found $cloudProvider/$account/$serverGroupName in $location")
    })

    def currentMin = Integer.parseInt(tsg.capacity.min.toString())
    def currentDesired = Integer.parseInt(tsg.capacity.desired.toString())
    def currentMax = Integer.parseInt(tsg.capacity.max.toString())

    return new ResizeStrategy.Capacity(currentMax, currentDesired, currentMin)
  }

  ResizeStrategy.Capacity pinMin(ResizeStrategy.Capacity capacity) {
    return new ResizeStrategy.Capacity(
      capacity.max,
      capacity.desired,
      capacity.desired
    )
  }

  ResizeStrategy.Capacity unpinMin(ResizeStrategy.Capacity capacity, Integer originalMin) {
    if (originalMin > capacity.min) {
      log.warn("Can not unpin the minimum of ${capacity} to a higher originalMin=${originalMin}")
      return capacity
    }

    return new ResizeStrategy.Capacity(
      capacity.max,
      capacity.desired,
      originalMin)
  }


  ResizeStrategy.Capacity pinCapacity(ResizeStrategy.Capacity capacity) {
    return new ResizeStrategy.Capacity(
      capacity.desired,
      capacity.desired,
      capacity.desired
    )
  }

  ResizeStrategy.Capacity scalePct(ResizeStrategy.Capacity capacity, double factor) {
    // scalePct only applies to the desired capacity
    def newDesired = (Integer) Math.ceil(capacity.desired * factor)
    return new ResizeStrategy.Capacity(
      Math.max(capacity.max, newDesired), // in case scalePct pushed desired above current max
      newDesired,
      Math.min(capacity.min, newDesired)) // in case scalePct pushed desired below current min
  }

  ResizeStrategy.Capacity performScalingAndPinning(Capacity sourceCapacity,
                                                   Stage stage,
                                                   ResizeStrategy.OptionalConfiguration resizeConfig) {
    ResizeStrategy.StageData stageData = stage.mapTo(ResizeStrategy.StageData)

    def newCapacity = sourceCapacity
    if (resizeConfig.scalePct != null) {
      double factor = resizeConfig.scalePct / 100.0d
      newCapacity = scalePct(sourceCapacity, factor)
    }

    if (stageData.unpinMinimumCapacity) {
      Integer originalMin = null
      def originalSourceCapacity = stage.context.get("originalCapacity.${stageData.source?.serverGroupName}".toString()) as Capacity
      originalMin = originalSourceCapacity?.min ?: stage.context.savedCapacity?.min

      if (originalMin != null) {
        newCapacity = unpinMin(newCapacity, originalMin as Integer)
      } else {
        log.warn("Resize stage has unpinMinimumCapacity==true but could not find the original minimum value in stage" +
          " context with stageData.source=${stageData.source}, stage.context.originalCapacity=${stage.context.originalCapacity}, " +
          "stage.context.savedCapacity=${stage.context.savedCapacity}")

        // stage ids and execution ids are both high cardinality, but we expect this to happen very rarely (if ever)
        registry.counter(
          "orca.failedUnpinMin",
          "stage", stage.id,
          "execution", stage.execution.id)
          .increment()
      }
    }

    if (stageData.pinCapacity) {
      return pinCapacity(newCapacity)
    }

    return stageData.pinMinimumCapacity ? pinMin(newCapacity) : newCapacity
  }
}
