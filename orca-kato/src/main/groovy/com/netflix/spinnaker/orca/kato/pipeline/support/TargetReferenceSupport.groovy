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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class TargetReferenceSupport {
  @Autowired
  ObjectMapper mapper

  @Autowired
  OortService oort

  /**
   * This method assumes that the caller will be looking to get a handle on an ASG by a reference key, in a cross-region
   * manner. For example, the desire from the caller is likely to have the engine lookup the "last_asg" (ie. asg at n-1
   * position) for a provided "cluster" in a set of regions.
   *
   * The incoming configuration structure must have the following attributes:
   * <pre>
   *{*     "cluster": "kato-main",
   *     "target": "last_asg",
   *     "regions": ["us-west-1", "us-east-1"],
   *     "credentials": "test"
   *}* </pre>
   *
   * This method will also create references for an exact ASG target when no <em>target</em> field is specified, and an
   * <em>asgName</em> field is specified. That it to say, the following configuration will lookup the specific ASG in
   * both "us-west-1" and "us-east-1" regions:
   *
   * <pre>
   *{*     "asgName": "kato-main-v000",
   *     "regions": ["us-west-1", "us-east-1"],
   *     "credentials": "test"
   *}* </pre>
   *
   * If <em>asgName</em> is specified and the engine cannot find that named ASG in one of the specified regions, that
   * target object will be discarded from the result set without failure.
   *
   * @see TargetReferenceConfiguration
   *
   * @param stage - used to convert the stage context to a {@link TargetReference} type
   * @return a list of ASG references, collated by their region
   */
  List<TargetReference> getTargetAsgReferences(Stage stage) {
    def config = stage.mapTo(TargetReferenceConfiguration)
    if (isDynamicallyBound(stage) && stage.type != DetermineTargetReferenceStage.MAYO_CONFIG_TYPE) {
      def target = stage.execution.stages.find {
        it.type == DetermineTargetReferenceStage.MAYO_CONFIG_TYPE && (it.parentStageId == stage.parentStageId || it.parentStageId == stage.id)
      }
      if (target?.context?.targetReferences) {
        return target.context.targetReferences.collect {
          new TargetReference(region: it.region, cluster: it.cluster, asg: it.asg)
        }
      } else {
        return config.regions.collect { new TargetReference(region: it, cluster: config.cluster) }
      }
    }

    if ((!config.target || !config.cluster) && (!config.asgName)) {
      return null
    }

    def names = Names.parseName(config.cluster ?: config.asgName)
    def existingAsgs = getExistingAsgs(names.app, config.credentials, names.cluster, config.providerType)
    if (!existingAsgs) {
      if (isDynamicallyBound(stage)) {
        return config.regions.collect {
          new TargetReference(region: it, cluster: config.cluster)
        }
      } else {
        throw new TargetReferenceNotFoundException("Could not ascertain targets for cluster ${names.cluster} " +
          "in ${config.credentials} (regions: ${config.regions.join(',')})")
      }
    }

    Map<String, List<Map>> asgsByRegion = (Map<String, List<Map>>) existingAsgs.groupBy { Map asg -> asg.region }
    List<TargetReference> targetReferences = []
    for (Map.Entry<String, List<Map>> entry in asgsByRegion) {
      def region = entry.key
      if (!config.regions || !config.regions.contains(region)) {
        continue
      }

      def sortedAsgs = entry.value.sort { a, b -> b.createdTime <=> a.createdTime }
      def asgCount = sortedAsgs.size()

      def targetReference = new TargetReference(region: region, cluster: config.cluster)
      if (isCurrentAsg(config)) {
        targetReference.asg = sortedAsgs.get(0)
      } else if (isAncestorAsg(config)) {
        // because of the groupBy above, there will be at least one - no need to check for zero
        if (asgCount == 1) {
          throw new TargetReferenceNotFoundException("Only one server group (${sortedAsgs.get(0).name}) found for " +
            "cluster ${config.cluster} in ${config.credentials}:${region} - no ancestor available")
        }
        targetReference.asg = sortedAsgs.get(1)
      } else if (isOldestAsg(config)) {
        // because of the groupBy above, there will be at least one - no need to check for zero
        if (asgCount == 1) {
          throw new TargetReferenceNotFoundException("Only one server group (${sortedAsgs.get(0).name}) found for " +
            "cluster ${config.cluster} in ${config.credentials}:${region} - at least two expected")
        }
        targetReference.asg = sortedAsgs.last()
      } else if (!config.target && config.asgName) {
        def asg = sortedAsgs.find { it.name == config.asgName }
        if (!asg) {
          // Couldn't find the specified ASG in this region.
          // This is probably OK, as the target region may be a transient deployment target
          continue
        } else {
          targetReference.asg = asg
        }
      }

      if (targetReference.asg) {
        targetReferences << targetReference
      }
    }

    targetReferences
  }

  TargetReference getDynamicallyBoundTargetAsgReference(Stage stage) {
    getTargetAsgReferences(stage).find {
      ((List) stage.context.regions).contains(it.region)
    }
  }

  private static boolean isCurrentAsg(config) {
    TargetReferenceConfiguration.Target.current_asg == config.target ||
      TargetReferenceConfiguration.Target.current_asg_dynamic == config.target
  }

  private static boolean isAncestorAsg(config) {
    TargetReferenceConfiguration.Target.ancestor_asg == config.target ||
      TargetReferenceConfiguration.Target.ancestor_asg_dynamic == config.target
  }

  private static boolean isOldestAsg(config) {
    TargetReferenceConfiguration.Target.oldest_asg_dynamic == config.target
  }

  boolean isDynamicallyBound(Stage stage) {
    def config = stage.mapTo(TargetReferenceConfiguration)
    config.target == TargetReferenceConfiguration.Target.ancestor_asg_dynamic ||
      config.target == TargetReferenceConfiguration.Target.current_asg_dynamic ||
      config.target == TargetReferenceConfiguration.Target.oldest_asg_dynamic
  }

  List<Map> getExistingAsgs(String app, String account, String cluster, String providerType) {
    try {
      def response = oort.getCluster(app, account, cluster, providerType)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups as List<Map>
    } catch (e) {
      null
    }
  }
}
