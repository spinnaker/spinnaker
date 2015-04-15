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

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component
@Slf4j
class CleanupCanaryTask implements Task {

  @Autowired KatoService katoService

  @Override
  TaskResult execute(Stage stage) {
    Canary canary = stage.mapTo('/canary', Canary)
    String operation = 'destroyAsgDescription'
    def operations = []
    for (d in canary.canaryDeployments) {
      for (c in [d.baselineCluster, d.canaryCluster]) {
        operations << [(operation): [asgName: c.name, regions: [c.region], credentials: c.accountName]]
      }
    }
    log.info "Calling ${operation} with ${operations}"
    def taskId = katoService.requestOperations(operations).toBlocking().first()
    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, ['kato.last.task.id': taskId])
  }
}
