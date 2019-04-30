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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.instance.TerminatingInstance
import com.netflix.spinnaker.orca.clouddriver.pipeline.instance.TerminatingInstanceSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class WaitForTerminatedInstancesTask extends AbstractCloudProviderAwareTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = 10000
  long timeout = 3600000

  @Autowired
  TerminatingInstanceSupport instanceSupport

  @Override
  TaskResult execute(Stage stage) {
    List<TerminatingInstance> remainingInstances = instanceSupport.remainingInstances(stage)
    return remainingInstances ?
        TaskResult.builder(ExecutionStatus.RUNNING).context([(TerminatingInstanceSupport.TERMINATE_REMAINING_INSTANCES): remainingInstances]).build() :
      TaskResult.SUCCEEDED
  }
}
