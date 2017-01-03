/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.StartStopAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired


abstract class AbstractStartStopAppEngineAtomicOperation implements AtomicOperation<Void> {
  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  abstract String getBasePhase()

  abstract boolean isStart()

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final StartStopAppEngineDescription description

  AbstractStartStopAppEngineAtomicOperation(StartStopAppEngineDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = start ? 'start' : 'stop'
    String presentParticipling = start ? 'Starting' : 'Stopping'

    task.updateStatus basePhase, "Initializing $verb server group operation on $description.serverGroupName in" +
      " $description.credentials.region..."

    def credentials = description.credentials
    def serverGroupName = description.serverGroupName
    def appengine = credentials.appengine
    def project = credentials.project

    task.updateStatus basePhase, "Looking up server group $serverGroupName..."
    def serverGroup = appEngineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def loadBalancerName = serverGroup.loadBalancers.first()

    def newServingStatus = start ? AppEngineServerGroup.ServingStatus.SERVING : AppEngineServerGroup.ServingStatus.STOPPED
    def version = new Version(servingStatus: newServingStatus.toString())

    task.updateStatus basePhase, "$presentParticipling $serverGroupName..."
    appengine.apps().services().versions().patch(project, loadBalancerName, serverGroupName, version)
      .setUpdateMask("servingStatus")
      .execute()

    task.updateStatus basePhase, "Done ${presentParticipling.toLowerCase()} $serverGroupName."
    return null
  }
}
