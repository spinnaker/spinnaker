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

import com.netflix.spinnaker.orca.pipeline.Stage

abstract class AbstractWaitingForInstancesTask extends AbstractInstancesCheckTask {
  @Override
  protected Map<String, List<String>> getServerGroups(Stage stage) {
    def key = "disableAsg"
    Map<String, List<String>> serverGroups = [:]

    if (stage.context.containsKey("targetop.asg.enableAsg.name")) {
      key = "enableAsg"
    }

    String asgName = stage.context."targetop.asg.${key}.name".toString()
    List<String> regions = stage.context."targetop.asg.${key}.regions"

    if (!asgName || !regions) {
      if (stage.context.containsKey("deploy.server.groups")) {
        serverGroups = (Map<String, List<String>>)stage.context."deploy.server.groups"
      }
    } else {
      regions.each { region ->
        if (!serverGroups.containsKey(region)) {
          serverGroups[region] = [asgName]
        }
        serverGroups[region] << asgName
      }
    }
    serverGroups
  }
}
