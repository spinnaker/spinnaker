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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * TODO: This task can be moved to clouddriver.tasks package once the convert() method has been cleaned up using the new oort APIs
 */
@Component
@Slf4j
class DestroyServerGroupTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    Map context = convert(stage)
    String cloudProvider = getCloudProvider(stage)
    TaskId taskId = kato.requestOperations(cloudProvider, [[destroyServerGroup: context]])
      .toBlocking()
      .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : "destroyservergroup",
      "deploy.account.name" : context.credentials,
      "kato.last.task.id"   : taskId,
      "asgName"             : context.serverGroupName,  // TODO: Retire asgName
      "serverGroupName"     : context.serverGroupName,
      "deploy.server.groups": context.regions.collectEntries { [(it): [context.serverGroupName]] }
    ])
  }

  Map convert(Stage stage) {
    def context = new HashMap(stage.context)
    context.serverGroupName = (context.serverGroupName ?: context.asgName) as String

    if (TargetServerGroup.isDynamicallyBound(stage)) {
      def tsg = TargetServerGroupResolver.fromPreviousStage(stage)
      context.asgName = tsg.cluster
      context.serverGroupName = tsg.cluster

      if (context.zones && context.zones.contains(tsg.location)) {
        context.zone = tsg.location
        context.remove("zones")
      }
    }

    context
  }
}
