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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DestroyAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppEngineResourceNotFoundException
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class DestroyAppEngineAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DestroyAppEngineDescription description

  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  DestroyAppEngineAtomicOperation(DestroyAppEngineDescription description) {
    this.description = description
  }
  /**
  * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyServerGroup": { "serverGroupName": "app-stack-detail-v000", "credentials": "my-appengine-account" }} ]' localhost:7002/appengine/ops
  */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destruction of server group $description.serverGroupName..."

    def credentials = description.credentials
    def appengine = credentials.appengine
    def project = credentials.project
    def serverGroupName = description.serverGroupName

    task.updateStatus BASE_PHASE, "Looking up $description.serverGroupName..."
    def serverGroup = appEngineClusterProvider.getServerGroup(credentials.name,
                                                              credentials.region,
                                                              serverGroupName)
    def loadBalancerName = serverGroup?.loadBalancers?.first()

    if (!serverGroup || !loadBalancerName) {
      throw new AppEngineResourceNotFoundException("Unable to locate server group $serverGroupName")
    }

    appengine.apps().services().versions().delete(project, loadBalancerName, serverGroupName).execute()

    task.updateStatus BASE_PHASE, "Successfully destroyed server group $serverGroupName."
  }
}
