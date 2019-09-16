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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.EnableDisablePercentageCategorizer
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.EnableDisableKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesServerGroupDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1ServerGroup
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.KubernetesV1ClusterProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Slf4j
abstract class AbstractEnableDisableKubernetesAtomicOperation implements AtomicOperation<Void> {
  abstract String getBasePhase() // Either 'ENABLE' or 'DISABLE'.
  abstract String getAction() // Either 'true' or 'false', for Enable or Disable respectively.
  abstract String getVerb() // Either 'enabling' or 'disabling.
  EnableDisableKubernetesAtomicOperationDescription description

  AbstractEnableDisableKubernetesAtomicOperation(KubernetesServerGroupDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  KubernetesV1ClusterProvider clusterProviders

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus basePhase, "Initializing ${basePhase.toLowerCase()} operation for ${description.serverGroupName}..."
    task.updateStatus basePhase, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)
    def desiredPercentage = description.desiredPercentage ?: 100
    def pods = []

    task.updateStatus basePhase, "Finding requisite server group..."

    def replicationController = credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)
    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)

    if (!replicationController && !replicaSet) {
      if (!supportsEnableDisable(credentials, description.serverGroupName, namespace)) {
        return
      }
      throw new KubernetesOperationException("Only support operation for replication controller or replica set $description.serverGroupName in $namespace.")
    }

    // If we edit the spec when disabling less than 100% of pods, we won't be able to handle autoscaling
    // actively correctly.
    if (desiredPercentage == 100 || action == "true") {
      task.updateStatus basePhase, "Getting list of attached services..."

      List<String> services = KubernetesUtil.getLoadBalancers(replicationController ?: replicaSet)
      services = services.collect {
        KubernetesUtil.loadBalancerKey(it)
      }

      task.updateStatus basePhase, "Resetting server group service template labels and selectors..."

      def getGeneration = null
      def getResource = null
      def desired = null
      def disableAnnotation = null
      if (replicationController) {
        desired = credentials.apiAdaptor.toggleReplicationControllerSpecLabels(namespace, description.serverGroupName, services, action)
        getGeneration = { ReplicationController rc ->
          return rc.metadata.generation
        }
        getResource = {
          return credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)
        }
        disableAnnotation = { ->
          return credentials.apiAdaptor.annotateReplicationController(namespace, description.serverGroupName, KubernetesUtil.ENABLE_DISABLE_ANNOTATION, action)
        }
      } else if (replicaSet) {
        desired = credentials.apiAdaptor.toggleReplicaSetSpecLabels(namespace, description.serverGroupName, services, action)
        getGeneration = { ReplicaSet rs ->
          return rs.metadata.generation
        }
        getResource = {
          return credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)
        }
        disableAnnotation = { ->
          return credentials.apiAdaptor.annotateReplicaSet(namespace, description.serverGroupName, KubernetesUtil.ENABLE_DISABLE_ANNOTATION, action)
        }
      } else {
        throw new KubernetesOperationException("No replication controller or replica set $description.serverGroupName in $namespace.")
      }

      if (!credentials.apiAdaptor.blockUntilResourceConsistent(desired, getGeneration, getResource)) {
        throw new KubernetesOperationException("Server group failed to reach a consistent state while waiting for label to be applied. This is likely a bug with Kubernetes itself.")
      }

      if (!credentials.apiAdaptor.blockUntilResourceConsistent(disableAnnotation(), getGeneration, getResource)) {
        throw new KubernetesOperationException("Server group failed to reach a consistent state while waiting for annotation be applied. This is likely a bug with Kubernetes itself.")
      }
    }

    if (!replicationController && !replicaSet ) {
      throw new KubernetesOperationException("No replication controller or replica set $description.serverGroupName in $namespace.")
    }

    KubernetesV1ServerGroup serverGroup = clusterProviders.getServerGroup(description.account, namespace, description.serverGroupName)
    serverGroup.instances.forEach( { instance -> pods.add(instance.getPod())})

    if (!pods) {
      task.updateStatus basePhase, "No pods to ${basePhase.toLowerCase()}. Operation finshed successfully."
      return
    }

    task.updateStatus basePhase, "Resetting service labels for each pod..."

    def pool = Executors.newWorkStealingPool((int) (pods.size() / 2) + 1)

    if (desiredPercentage != null) {
      task.updateStatus basePhase, "Operating on $desiredPercentage% of pods"
      List<Pod> modifiedPods = pods.findAll { pod ->
        KubernetesUtil.getPodLoadBalancerStates(pod).every { it.value == action }
      }

      List<Pod> unmodifiedPods = pods.findAll { pod ->
        KubernetesUtil.getPodLoadBalancerStates(pod).any { it.value != action }
      }

      pods = EnableDisablePercentageCategorizer.getInstancesToModify(modifiedPods, unmodifiedPods, desiredPercentage)
    }

    pods.each { Pod pod ->
      pool.submit({ _ ->
        List<String> podServices = KubernetesUtil.getLoadBalancers(pod)
        podServices = podServices.collect {
          KubernetesUtil.loadBalancerKey(it)
        }
        credentials.apiAdaptor.togglePodLabels(namespace, pod.metadata.name, podServices, action)
      })
    }

    pool.shutdown()
    pool.awaitTermination(1, TimeUnit.HOURS)

    task.updateStatus basePhase, "Finished ${verb} server group ${description.serverGroupName}."

    null // Return nothing from void
  }

  boolean supportsEnableDisable(KubernetesV1Credentials credentials, String serverGroupName, String namespace) {
    def controllerKind = description.kind
    if (!description.kind) {
      controllerKind = credentials.clientApiAdaptor.getControllerKind(serverGroupName, namespace, null)
    }
    switch (controllerKind) {
      //disable/enable statefulset and daemonset server group operations are not support
      case KubernetesUtil.CONTROLLERS_STATEFULSET_KIND:
        log.info("Skip disable/enable StatefuSet server group $description.serverGroupName in $description.namespace because not applicible.")
        task.updateStatus basePhase, "Skip disable/enable StatefuSet server group $description.serverGroupName in $description.namespace."
        return false
      case KubernetesUtil.CONTROLLERS_DAEMONSET_KIND:
        log.info("Skip disable/enable DaemonSet server group $description.serverGroupName in $description.namespace because not applicible.")
        task.updateStatus basePhase, "Skip disable/enable DaemonSet server group $description.serverGroupName in $description.namespace."
        return false
      case KubernetesUtil.SERVER_GROUP_KIND:
      case KubernetesUtil.DEPRECATED_SERVER_GROUP_KIND:
      default:
        return true
    }
  }
}
