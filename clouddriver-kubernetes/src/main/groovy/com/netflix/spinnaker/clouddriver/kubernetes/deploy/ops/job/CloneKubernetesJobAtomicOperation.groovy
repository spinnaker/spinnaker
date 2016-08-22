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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops.job

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.job.CloneKubernetesJobAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.job.KubernetesJobRestartPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesResourceNotFoundException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.extensions.Job

class CloneKubernetesJobAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CLONE_JOB"

  CloneKubernetesJobAtomicOperation(CloneKubernetesJobAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  CloneKubernetesJobAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "cloneJob": { "source": { "jobName": "kub-test-v000" }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
  */
  @Override
  DeploymentResult operate(List priorOutputs) {
    CloneKubernetesJobAtomicOperationDescription newDescription = cloneAndOverrideDescription()

    task.updateStatus BASE_PHASE, "Initializing copy of job for ${description.source.jobName}..."

    RunKubernetesJobAtomicOperation deployer = new RunKubernetesJobAtomicOperation(newDescription)
    DeploymentResult deploymentResult = deployer.operate(priorOutputs)

    task.updateStatus BASE_PHASE, "Finished copying job for ${description.source.jobName}."

    task.updateStatus BASE_PHASE, "Finished copying job for ${description.source.jobName}. New job = ${deploymentResult.deployedNames[0]}."

    return deploymentResult
  }

  CloneKubernetesJobAtomicOperationDescription cloneAndOverrideDescription() {
    CloneKubernetesJobAtomicOperationDescription newDescription = description.clone()

    task.updateStatus BASE_PHASE, "Reading ancestor job ${description.source.jobName}..."

    def credentials = newDescription.credentials.credentials

    newDescription.source.namespace = description.source.namespace ?: "default"
    Job ancestorJob = credentials.apiAdaptor.getJob(newDescription.source.namespace, newDescription.source.jobName)

    if (!ancestorJob) {
      throw new KubernetesResourceNotFoundException("Source job $newDescription.source.jobName does not exist.")
    }

    def ancestorNames = Names.parseName(description.source.jobName)

    // Build description object from ancestor, override any values that were specified on the clone call
    newDescription.application = description.application ?: ancestorNames.app
    newDescription.stack = description.stack ?: ancestorNames.stack
    newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    newDescription.parallelism = description.parallelism ?: ancestorJob.spec?.parallelism
    newDescription.completions = description.completions ?: ancestorJob.spec?.completions
    newDescription.namespace = description.namespace ?: description.source.namespace
    newDescription.loadBalancers = description.loadBalancers != null ? description.loadBalancers : KubernetesUtil.getLoadBalancers(ancestorJob)
    newDescription.restartPolicy = description.restartPolicy ?: KubernetesJobRestartPolicy.fromString(ancestorJob.spec?.template?.spec?.restartPolicy)
    if (!description.containers) {
      newDescription.containers = ancestorJob.spec?.template?.spec?.containers?.collect { it ->
        KubernetesApiConverter.fromContainer(it)
      }
    }

    return newDescription
  }
}
