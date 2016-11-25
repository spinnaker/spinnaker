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
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Deprecated
@Component
@Slf4j
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
    if (isDynamicallyBound(stage) && !isDTRStage(stage)) {
      // The DetermineTargetReferences stage has all the TargetReferences we want - go find it!
      def dtrStage = stage.execution.stages.find {
        isDTRStage(it) && (sameParent(stage, it) || isParentOf(stage, it))
      }
      if (dtrStage?.context?.targetReferences) {
        return dtrStage.context.targetReferences.collect {
          new TargetReference(region: it.region, cluster: it.cluster, asg: it.asg)
        }
      } else {
        return config.locations.collect { new TargetReference(region: it, cluster: config.cluster) }
      }
    }

    if ((!config.target || !config.cluster) && (!config.asgName)) {
      return null
    }

    def names = Names.parseName(config.cluster ?: config.asgName)
    def existingServerGroups = getExistingServerGroups(names.app, config.credentials, names.cluster, config.cloudProvider ?: config.providerType)
    if (!existingServerGroups) {
      if (isDynamicallyBound(stage)) {
        return config.locations.collect {
          new TargetReference(region: it, cluster: config.cluster)
        }
      } else {
        throw new TargetReferenceNotFoundException("Could not ascertain targets for cluster ${names.cluster} " +
          "in ${config.credentials} (regions: ${config.locations.join(',')})")
      }
    }

    def serverGroupsByLocation = getServerGroupsByLocation(config, existingServerGroups)
    List<TargetReference> targetReferences = []
    for (Map.Entry<String, List<Map>> entry in serverGroupsByLocation) {
      def location = entry.key
      if (!config.locations || !config.locations.contains(location)) {
        continue
      }

      def sortedServerGroups = entry.value.sort { a, b -> b.createdTime <=> a.createdTime }
      def asgCount = sortedServerGroups.size()

      def targetReference = new TargetReference(region: location, cluster: config.cluster)
      if (config.target) {
        switch (config.target) {
          case TargetReferenceConfiguration.Target.current_asg:
          case TargetReferenceConfiguration.Target.current_asg_dynamic:
            targetReference.asg = sortedServerGroups.get(0)
            break
          case TargetReferenceConfiguration.Target.ancestor_asg:
          case TargetReferenceConfiguration.Target.ancestor_asg_dynamic:
            // because of the groupBy above, there will be at least one - no need to check for zero
            if (asgCount == 1) {
              throw new TargetReferenceNotFoundException("Only one server group (${sortedServerGroups.get(0).name}) " +
                "found for cluster ${config.cluster} in ${config.credentials}:${location} - no ancestor available")
            }
            targetReference.asg = sortedServerGroups.get(1)
            break
          case TargetReferenceConfiguration.Target.oldest_asg_dynamic:
            // because of the groupBy above, there will be at least one - no need to check for zero
            if (asgCount == 1) {
              throw new TargetReferenceNotFoundException("Only one server group (${sortedServerGroups.get(0).name}) " +
                "found for cluster ${config.cluster} in ${config.credentials}:${location} - at least two expected")
            }
            targetReference.asg = sortedServerGroups.last()
            break
        }
      } else if (config.asgName) {
        targetReference.asg = sortedServerGroups.find { it.name == config.asgName }
        // if targetReference.asg is still null by this point, we couldn't find the specified ASG in this region.
        // This is probably OK, as the target region may be a transient deployment target.
      }

      if (targetReference.asg) {
        targetReferences << targetReference
      }
    }

    targetReferences
  }

  TargetReference getDynamicallyBoundTargetAsgReference(Stage stage) {
    def target = getTargetAsgReferences(stage).find {
      ((List) stage.context.regions).contains(it.region)
    }
    if (!target.asg) {
      throw new TargetReferenceNotFoundException("No target found for cluster '${target.cluster}' in region '${target.region}'")
    }
    target
  }

  static boolean isDTRStage(Stage stage) {
    return stage.type == DetermineTargetReferenceStage.PIPELINE_CONFIG_TYPE
  }

  static boolean sameParent(Stage a, Stage b) {
    return a.parentStageId == b.parentStageId
  }

  static boolean isParentOf(Stage a, Stage b) {
    return a.id == b.parentStageId
  }

  private Map<String, List<Map>> getServerGroupsByLocation(TargetReferenceConfiguration config, List<Map> existingServerGroups) {
    if (config.cloudProvider == "gce") {
      return existingServerGroups.groupBy { Map sg -> sg.zones[0] }
    }
    return existingServerGroups.groupBy { Map sg -> sg.region }
  }

  boolean isDynamicallyBound(Stage stage) {
    def config = stage.mapTo(TargetReferenceConfiguration)
    config.target == TargetReferenceConfiguration.Target.ancestor_asg_dynamic ||
      config.target == TargetReferenceConfiguration.Target.current_asg_dynamic ||
      config.target == TargetReferenceConfiguration.Target.oldest_asg_dynamic
  }

  List<Map> getExistingServerGroups(String app, String account, String cluster, String cloudProvider) {
    try {
      def response = oort.getCluster(app, account, cluster, cloudProvider)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups as List<Map>
    } catch (e) {
      null
    }
  }
}
