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

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

class WaitForUpInstancesTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 30000

  @Autowired
  OortService oortService

  @Override
  TaskResult execute(TaskContext context) {
    String account = context.inputs."deploy.account.name"
    Map<String, String> serverGroups = (Map<String, String>)context.inputs."deploy.server.groups"
    if (!serverGroups) {
      return new DefaultTaskResult(TaskResult.Status.FAILED)
    }
    Names names = Names.parseName(serverGroups.values()[0])
    oortService.getCluster()
  }
}
