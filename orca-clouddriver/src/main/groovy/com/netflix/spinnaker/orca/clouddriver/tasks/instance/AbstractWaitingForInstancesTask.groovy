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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.pipeline.model.Stage

abstract class AbstractWaitingForInstancesTask extends AbstractInstancesCheckTask {
  @Override
  protected Map<String, List<String>> getServerGroups(Stage stage) {
    Map<String, List<String>> serverGroups = [:]

    def keys = ["disableAsg", "enableAsg", "enableServerGroup", "disableServerGroup"]
    def key = keys.find { String k ->
      return stage.context.containsKey("targetop.asg.${k}.name".toString()) // stoopud gstrings.
    }
    String asgName = stage.context."targetop.asg.${key}.name".toString()
    List<String> regions = stage.context."targetop.asg.${key}.regions"

    if (!asgName || !regions) {
      if (stage.context.containsKey("deploy.server.groups")) {
        serverGroups = (Map<String, List<String>>) stage.context."deploy.server.groups"
      }
    } else {
      regions.each { region ->
        if (!serverGroups.containsKey(region)) {
          serverGroups[region] = []
        }
        serverGroups[region] << asgName
      }
    }
    serverGroups
  }

  // "Desired Percentage" implies that some fraction of the instances in a server group have been enabled
  // or disabled -- likely during a rolling red/black operation.
  protected static Integer getDesiredInstanceCount(Map capacity, Integer desiredPercentage) {
    if (desiredPercentage < 0 || desiredPercentage > 100) {
      throw new NumberFormatException("desiredPercentage must be an integer between 0 and 100")
    }

    Integer desired = capacity.desired as Integer
    Integer min = capacity.min != null ? (Integer) capacity.min : desired
    Integer max = capacity.max != null ? (Integer) capacity.max : desired

    // See https://docs.google.com/a/google.com/document/d/1rHe6JUkKGt58NaVO_3fHJt456Pw5kiX_sdVn1gwTZxk/edit?usp=sharing
    return Math.ceil(((desiredPercentage / 100D) - 1D) * max + min)
  }
}
