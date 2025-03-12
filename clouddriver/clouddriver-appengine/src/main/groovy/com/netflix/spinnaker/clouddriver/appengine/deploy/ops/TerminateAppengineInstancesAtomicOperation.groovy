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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.TerminateAppengineInstancesDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineInstanceProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class TerminateAppengineInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateAppengineInstancesDescription description

  @Autowired
  AppengineInstanceProvider appengineInstanceProvider

  TerminateAppengineInstancesAtomicOperation(TerminateAppengineInstancesDescription description) {
    this.description = description
  }
  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "instanceIds": ["instance-1"], "credentials": "my-appengine-account" } } ]' localhost:7002/appengine/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of instances (${description.instanceIds.join(", ")}) in " +
      "${description.credentials.region}..."

    def credentials = description.credentials
    def appengine = credentials.appengine
    def accountName = credentials.name
    def project = credentials.project

    def instanceIds = description.instanceIds
    def instances = instanceIds.collect { appengineInstanceProvider.getInstance(accountName, credentials.region, it) }

    instances.each { AppengineInstance instance ->
      def loadBalancerName = instance.loadBalancers[0]
      def serverGroupName = instance.serverGroup

      appengine
        .apps().services().versions().instances().delete(project, loadBalancerName, serverGroupName, instance.id).execute()
    }

    task.updateStatus BASE_PHASE, "Successfully terminated provided instances."
    return null
  }
}
