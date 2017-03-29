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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UpsertServerGroupTagsTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def taskId = kato.requestOperations(getCloudProvider(stage), [[upsertServerGroupTags: stage.context]])
        .toBlocking()
        .first()

    def deployServerGroups = []
    if (stage.context.regions && (stage.context.serverGroupName || stage.context.asgName)) {
      deployServerGroups = (stage.context.regions as Collection<String>).collectEntries {
        [(it): [stage.context.serverGroupName ?: stage.context.asgName]]
      }
    } else if (stage.context.asgs) {
      deployServerGroups = (stage.context.asgs as Collection<Map>).collectEntries {
        [(it.region): [it.serverGroupName ?: it.asgName]]
      }
    }

    new TaskResult(ExecutionStatus.SUCCEEDED, [
        "notification.type"   : "upsertservergrouptags",
        "deploy.account.name" : getCredentials(stage),
        "kato.last.task.id"   : taskId,
        "deploy.server.groups": deployServerGroups,
    ])
  }
}
