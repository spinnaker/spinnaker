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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.KubernetesNamedServicePort
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.UpsertKubernetesLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.ServicePort

class UpsertKubernetesLoadBalancerAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  UpsertKubernetesLoadBalancerAtomicOperationDescription description

  UpsertKubernetesLoadBalancerAtomicOperation(UpsertKubernetesLoadBalancerAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "upsertLoadBalancer": { "name": "service",  "ports": [ { "name": "http", "port": 80, "targetPort": 9376 } ], "credentials":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
  */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.name..."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.kubernetesCredentials
    def namespace = KubernetesUtil.validateNamespace(credentials, description.namespace)
    def name = description.name

    task.updateStatus BASE_PHASE, "Looking up existing load balancer..."
    def existingService = credentials.apiAdaptor.getService(namespace, name)

    if (existingService) {
      task.updateStatus BASE_PHASE, "Found existing load balancer with name $description.name."
    }

    def serviceBuilder = new ServiceBuilder()

    serviceBuilder = serviceBuilder.withNewMetadata().withName(name).endMetadata()

    task.updateStatus BASE_PHASE, "Setting label selectors..."

    serviceBuilder = serviceBuilder.withNewSpec()

    serviceBuilder = serviceBuilder.addToSelector(KubernetesUtil.loadBalancerKey(name), 'true')

    task.updateStatus BASE_PHASE, "Adding ports..."

    List<KubernetesNamedServicePort> ports = []

    for (ServicePort port : existingService?.spec?.ports) {
      def namedPort = new KubernetesNamedServicePort()
      port.name ? namedPort.name = port.name : null
      port.nodePort ? namedPort.nodePort = port.nodePort : null
      port.port ? namedPort.port = port.port : null
      port.targetPort ? namedPort.targetPort = port.targetPort?.intVal : null
      port.protocol ? namedPort.protocol = port.protocol : null
      ports << namedPort
    }

    ports = description.ports != null ? description.ports : ports

    for (def port : ports) {
      serviceBuilder = serviceBuilder.addNewPort()

      serviceBuilder = port.name ? serviceBuilder.withName(port.name) : serviceBuilder
      serviceBuilder = port.targetPort ? serviceBuilder.withNewTargetPort(port.targetPort) : serviceBuilder
      serviceBuilder = port.port ? serviceBuilder.withPort(port.port) : serviceBuilder
      serviceBuilder = port.nodePort ? serviceBuilder.withNodePort(port.nodePort) : serviceBuilder
      serviceBuilder = port.protocol ? serviceBuilder.withProtocol(port.protocol) : serviceBuilder

      serviceBuilder = serviceBuilder.endPort()
    }

    task.updateStatus BASE_PHASE, "Adding external IPs..."

    def externalIps = description.externalIps != null ? description.externalIps : existingService?.spec?.externalIPs

    for (def ip: externalIps) {
      serviceBuilder = serviceBuilder.addToExternalIPs(ip)
    }

    task.updateStatus BASE_PHASE, "Setting type..."

    def type = description.type != null ? description.type : existingService?.spec?.type
    serviceBuilder = type ? serviceBuilder.withType(type) : serviceBuilder

    task.updateStatus BASE_PHASE, "Setting load balancer IP..."

    def loadBalancerIp = description.loadBalancerIp != null ? description.loadBalancerIp : existingService?.spec?.loadBalancerIP
    serviceBuilder = loadBalancerIp ? serviceBuilder.withLoadBalancerIP(loadBalancerIp) : serviceBuilder

    task.updateStatus BASE_PHASE, "Setting cluster IP..."

    def clusterIp = description.clusterIp != null ? description.clusterIp : existingService?.spec?.clusterIP
    serviceBuilder = clusterIp ? serviceBuilder.withClusterIP(clusterIp) : serviceBuilder

    task.updateStatus BASE_PHASE, "Setting session affinity..."

    def sessionAffinity = description.sessionAffinity != null ? description.sessionAffinity : existingService?.spec?.sessionAffinity
    serviceBuilder = sessionAffinity ? serviceBuilder.withSessionAffinity(sessionAffinity) : serviceBuilder

    serviceBuilder = serviceBuilder.endSpec()

    def service = existingService ?
      credentials.apiAdaptor.replaceService(namespace, name, serviceBuilder.build()) :
      credentials.apiAdaptor.createService(namespace, serviceBuilder.build())

    task.updateStatus BASE_PHASE, "Finished upserting load balancer $description.name."

    [loadBalancers: [(service.metadata.namespace): [name: service.metadata.name]]]
  }
}
