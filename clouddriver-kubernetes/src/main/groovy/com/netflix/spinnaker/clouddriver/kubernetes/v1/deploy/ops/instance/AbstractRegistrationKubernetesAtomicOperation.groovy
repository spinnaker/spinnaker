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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.instance.AbstractRegistrationKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

abstract class AbstractRegistrationKubernetesAtomicOperation implements AtomicOperation<Void> {
  abstract String getBasePhase() // Either 'REGISTER' or 'DEREGISTER'.
  abstract String getAction() // Either 'true' or 'false', for Register and Deregister respectively.
  abstract String getVerb() // Either 'registering' or 'deregistering'.

  AbstractRegistrationKubernetesAtomicOperationDescription description

  AbstractRegistrationKubernetesAtomicOperation(AbstractRegistrationKubernetesAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus basePhase, "Initializing ${basePhase.toLowerCase()} operation..."
    task.updateStatus basePhase, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    def services = description.loadBalancerNames.collect {
      KubernetesUtil.loadBalancerKey(it)
    }

    task.updateStatus basePhase, "Setting new service labels from each pod..."

    description.instanceIds.each {
      credentials.apiAdaptor.togglePodLabels(namespace, it, services, action)
    }

    task.updateStatus basePhase, "Finished $verb all pods."
  }
}
