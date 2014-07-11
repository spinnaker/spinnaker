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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.TaskContext

class AsgActionWaitForDownInstancesTask extends WaitForDownInstancesTask {
  @Override
  protected Map<String, List<String>> getServerGroups(TaskContext context) {
    def inputMap = context.getInputs()
    def key = "disableAsg"

    if (inputMap.containsKey("targetop.asg.enableAsg.name")) {
      key = "enableAsg"
    }

    String asgName = inputMap."targetop.asg.enableAsg.name"
    List<String> regions = inputMap."targetop.asg.enableAsg.regions"

    Map<String, List<String>> serverGroups = [:]

    regions.each { region ->
      if (!serverGroups.containsKey(region)) {
        serverGroups[region] = [asgName]
      }
      serverGroups[region] << asgName
    }
    serverGroups
  }
}
