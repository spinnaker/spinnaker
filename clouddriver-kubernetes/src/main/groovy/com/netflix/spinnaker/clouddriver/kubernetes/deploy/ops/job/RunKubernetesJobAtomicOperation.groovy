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

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesJobNameResolver
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.job.KubernetesJobRestartPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.job.RunKubernetesJobDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.extensions.Job
import io.fabric8.kubernetes.api.model.extensions.JobBuilder

class RunKubernetesJobAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "RUN_JOB"

  RunKubernetesJobAtomicOperation(RunKubernetesJobDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  RunKubernetesJobDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "runJob": { "application": "kub", "stack": "test",  "parallelism": 1, "completions": 1, "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    Job job = jobDescription()
    return new DeploymentResult([
        deployedNames: [job.metadata.name],
        deployedNamesByLocation: [(job.metadata.namespace): [job.metadata.name]],
    ])
  }

  Job jobDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of job..."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)

    def jobNameResolver = new KubernetesJobNameResolver(namespace, credentials)
    def clusterName = jobNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${clusterName}..."
    def jobName = jobNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    task.updateStatus BASE_PHASE, "Job name chosen to be ${jobName}."

    def jobBuilder = new JobBuilder().withNewMetadata().withName(jobName).endMetadata()

    jobBuilder = jobBuilder.withNewSpec().withNewSelector().withMatchLabels([(KubernetesUtil.JOB_LABEL): jobName]).endSelector()

    task.updateStatus BASE_PHASE, "Setting completions and parallelism..."

    jobBuilder = jobBuilder.withParallelism(description.parallelism ?: 1).withCompletions(description.completions ?: 1)

    if (description.activeDeadlineSeconds) {
      jobBuilder = jobBuilder.withActiveDeadlineSeconds(description.activeDeadlineSeconds)
    }

    jobBuilder = jobBuilder.withNewTemplate().withNewMetadata()

    task.updateStatus BASE_PHASE, "Setting job spec labels..."

    jobBuilder = jobBuilder.addToLabels(KubernetesUtil.JOB_LABEL, jobName)

    for (def loadBalancer : description.loadBalancers) {
      jobBuilder = jobBuilder.addToLabels(KubernetesUtil.loadBalancerKey(loadBalancer), "true")
    }

    jobBuilder = jobBuilder.endMetadata().withNewSpec()

    switch (description.restartPolicy) {
      case KubernetesJobRestartPolicy.Never:
        jobBuilder = jobBuilder.withRestartPolicy("Never")
        break

      case KubernetesJobRestartPolicy.OnFailure:
      default:
        jobBuilder = jobBuilder.withRestartPolicy("OnFailure")
    }


    task.updateStatus BASE_PHASE, "Adding image pull secrets... "
    jobBuilder = jobBuilder.withImagePullSecrets()

    for (def imagePullSecret : credentials.imagePullSecrets[namespace]) {
      jobBuilder = jobBuilder.addNewImagePullSecret(imagePullSecret)
    }


    if (description.volumeSources) {
      def volumeSources = description.volumeSources.findResults { volumeSource ->
        KubernetesApiConverter.toVolumeSource(volumeSource)
      }

      jobBuilder = jobBuilder.withVolumes(volumeSources)
    }

    def containers = description.containers.collect { container ->
      KubernetesApiConverter.toContainer(container)
    }

    jobBuilder = jobBuilder.withContainers(containers)

    jobBuilder = jobBuilder.endSpec().endTemplate().endSpec()

    task.updateStatus BASE_PHASE, "Sending job spec to the Kubernetes master."
    Job job = credentials.apiAdaptor.createJob(namespace, jobBuilder.build())

    task.updateStatus BASE_PHASE, "Finished creating job ${job.metadata.name}."

    return job
  }
}
