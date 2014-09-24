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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import org.springframework.beans.factory.annotation.Autowired

class ServerGroupCacheForceRefreshTask implements Task {
  static final String REFRESH_TYPE = "AmazonServerGroup"

  @Autowired
  OortService oort

  @Override
  TaskResult execute(TaskContext context) {
    String account = context.getInputs()."deploy.account.name"
    Map<String, List<String>> capturedServerGroups = (Map<String, List<String>>)context.getInputs()."deploy.server.groups"
    capturedServerGroups.each { region, serverGroups ->
      for (serverGroup in serverGroups) {
        def model = [asgName: serverGroup, region: region, account: account]
        oort.forceCacheUpdate(REFRESH_TYPE, model)
      }
    }
    new DefaultTaskResult(TaskResult.Status.SUCCEEDED)
  }
}
