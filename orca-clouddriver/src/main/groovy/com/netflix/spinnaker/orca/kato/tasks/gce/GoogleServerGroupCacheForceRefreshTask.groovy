/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.gce

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class GoogleServerGroupCacheForceRefreshTask implements Task {
  static final String REFRESH_TYPE = "GoogleServerGroup"

  @Autowired
  OortService oort

  @Override
  TaskResult execute(Stage stage) {
    String account = stage.context."account.name"
    if (stage.context.account && !account) {
      account = stage.context.account
    } else if (stage.context.credentials && !account) {
      account = stage.context.credentials
    }

    def zone = stage.context.zone
    if (!zone) {
      zone = stage.context.zones ? stage.context.zones[0] : null
    }

    Map<String, List<String>> capturedServerGroups = (Map<String, List<String>>) stage.context."deploy.server.groups"
    def outputs = [:]
    capturedServerGroups.each { region, serverGroups ->
      for (serverGroup in serverGroups) {
        def model = [serverGroupName: serverGroup, region: region, zone: zone, account: account]

        try {
          oort.forceCacheUpdate(REFRESH_TYPE, model)
        } catch (e) {
          if (!outputs.containsKey("force.cache.refresh.errors")) {
            outputs["force.cache.refresh.errors"] = []
          }
          outputs["force.cache.refresh.errors"] << e.message
        }
      }
    }
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
