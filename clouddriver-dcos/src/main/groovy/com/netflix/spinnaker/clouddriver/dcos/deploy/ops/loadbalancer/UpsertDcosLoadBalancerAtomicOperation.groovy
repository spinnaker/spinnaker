/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.exception.DcosOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.*

class UpsertDcosLoadBalancerAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor
  private final DcosConfigurationProperties dcosConfigurationProperties

  UpsertDcosLoadBalancerAtomicOperationDescription description

  UpsertDcosLoadBalancerAtomicOperation(DcosClientProvider dcosClientProvider,
                                        DcosDeploymentMonitor deploymentMonitor,
                                        DcosConfigurationProperties dcosConfigurationProperties,
                                        UpsertDcosLoadBalancerAtomicOperationDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.deploymentMonitor = deploymentMonitor
    this.dcosConfigurationProperties = dcosConfigurationProperties
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Map operate(List priorOutputs) {
    // TODO currently implements a naive update. Not sure what we need to do yet here. Also, other cloud providers (like kubernetes) have
    // logic to update/patch given an existing load balancer as a base, but it appears that all the existing configuration gets passed down by deck anyway (meaning it always overrides everything).
    // I'm assuming we'll do it the same way.

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials, description.dcosCluster)

    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.name..."
    task.updateStatus BASE_PHASE, "Looking up existing load balancer..."

    def appId = DcosSpinnakerLbId.fromVerbose(description.credentials.account, description.name).get()

    def existingLb = dcosClient.maybeApp(appId.toString()).orElse(null)

    if (existingLb)
      task.updateStatus BASE_PHASE, "Existing load balancer with name $description.name found - modifying it."
    else
      task.updateStatus BASE_PHASE, "No existing load balancer with name $description.name found - creating it."

    def lbDefinition = createLoadBalancerDefinition(appId)

    def deploymentId
    def newLb
    if (existingLb) {
      def result = dcosClient.updateApp(lbDefinition.id, lbDefinition, false)
      newLb = existingLb
      deploymentId = result.deploymentId
    } else {
      newLb = dcosClient.createApp(lbDefinition)
      deploymentId = newLb.deployments.get(0).id
    }

    def deploymentResult = deploymentMonitor.waitForAppDeployment(dcosClient, newLb, deploymentId, null, task, BASE_PHASE)

    if (!deploymentResult.success) {
      throw new DcosOperationException("Failed to upsert load balancer $description.name.")
    }

    task.updateStatus BASE_PHASE, "Finished upserting load balancer $description.name."

    [loadBalancer: [name: deploymentResult.deployedApp.get().id]]
  }

  private App createLoadBalancerDefinition(final DcosSpinnakerLbId appId) {

    new App().with {

      id = appId.toString()

      args = ["sse",
              "-m",
              "http://master.mesos:8080",
              "--health-check",
              "--haproxy-map",
              "--group",
              // TODO Need a load balancer specific id type where I can do this type of stuff instead.
              appId.loadBalancerHaproxyGroup]

      // TODO Expose configuration? these are defaults based on the current universe package
      env = ["HAPROXY_SSL_CERT"     : "",
             "HAPROXY_SYSCTL_PARAMS": "net.ipv4.tcp_tw_reuse=1 net.ipv4.tcp_fin_timeout=30 net.ipv4.tcp_max_syn_backlog=10240 net.ipv4.tcp_max_tw_buckets=400000 net.ipv4.tcp_max_orphans=60000 net.core.somaxconn=10000"]

      def cluster = dcosConfigurationProperties.clusters.find {it.name == description.dcosCluster}
      def loadBalancerConfig = cluster?.loadBalancer

      if (loadBalancerConfig?.serviceAccountSecret) {
        env.put("DCOS_SERVICE_ACCOUNT_CREDENTIAL", ["secret": "serviceCredential"])
      }

      instances = description.instances
      cpus = description.cpus
      mem = description.mem

      container = new Container().with {
        type = "DOCKER"
        docker = new Docker().with {
          image = loadBalancerConfig?.image
          network = "HOST"
          privileged = true
          forcePullImage = false
          it
        }
        it
      }

      healthChecks = [
              new HealthCheck().with {
                protocol = "HTTP"
                path = "/_haproxy_health_check"
                gracePeriodSeconds = 60
                intervalSeconds = 5
                timeoutSeconds = 5
                maxConsecutiveFailures = 2
                portIndex = 0
                ignoreHttp1xx = false
                it
              }
      ]

      labels = ["SPINNAKER_LOAD_BALANCER": description.name]

      acceptedResourceRoles = description.acceptedResourceRoles?.empty ? null : description.acceptedResourceRoles

      if (loadBalancerConfig?.serviceAccountSecret) {
        secrets = [serviceCredential: [source: loadBalancerConfig?.serviceAccountSecret]]
      }

      portDefinitions = []

      // Always required
      portDefinitions.addAll(
              new PortDefinition(protocol: "tcp", port: 9090),
              new PortDefinition(protocol: "tcp", port: 9091))

      if (description.bindHttpHttps) {
        portDefinitions.addAll(
                new PortDefinition(protocol: "tcp", port: 80),
                new PortDefinition(protocol: "tcp", port: 443))
      }

      for (int port = description.portRange.minPort; port < description.portRange.maxPort + 1; port++) {
        portDefinitions.add(new PortDefinition(protocol: description.portRange.protocol.toLowerCase(), port: port));
      }

      requirePorts = true
      it
    }
  }
}
