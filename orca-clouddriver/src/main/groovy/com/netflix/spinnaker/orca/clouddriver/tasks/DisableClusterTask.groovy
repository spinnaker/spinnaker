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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import org.springframework.stereotype.Component

@Component
class DisableClusterTask extends AbstractClusterWideClouddriverTask {

  @Override
  String getClouddriverOperation() {
    "disableServerGroup"
  }

  @Canonical
  static class DisableClusterConfig {
    int remainingEnabledServerGroups = 1
    boolean preferLargerOverNewer = false
  }

  @Override
  List<Map> filterServerGroups(Stage stage, String account, String region, List<Map> serverGroups) {
    List<Map> filtered = super.filterServerGroups(stage, account, region, serverGroups)

    def config = stage.mapTo(DisableClusterConfig)
    int dropCount = Math.max(0, config.remainingEnabledServerGroups - (serverGroups.size() - filtered.size()))

    filtered = filtered.findAll { isActive(it) }

    Comparator<Map> comparator = config.preferLargerOverNewer ?
      new InstanceCount() :
      new CreatedTime()

    return filtered.sort(true, comparator).drop(dropCount)
  }
}
