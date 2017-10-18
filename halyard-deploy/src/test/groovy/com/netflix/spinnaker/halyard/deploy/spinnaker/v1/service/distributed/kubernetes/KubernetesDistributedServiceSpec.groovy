/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentEnvironment
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceInterfaceFactory
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerMonitoringDaemonService
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService
import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesDistributedServiceSpec extends Specification {

    @Unroll()
    def "applies request and limit overrides: #description"() {
        setup:
        KubernetesContainerDescription container = new KubernetesContainerDescription()
        def service = createServiceTestDouble()
        def deploymentEnvironment = new DeploymentEnvironment()
        deploymentEnvironment.customSizing["echo"] = new HashMap<>(requests: requests, limits: limits)

        when:
        service.applyCustomSize(container, deploymentEnvironment, "echo")

        then:
        container.requests?.memory == requestsMemory
        container.requests?.cpu == requestsCpu
        container.limits?.memory == limitsMemory
        container.limits?.cpu == limitsCpu

        where:
        description         | requests                                | limits                                    | requestsMemory | requestsCpu | limitsMemory | limitsCpu
        "all"               | new HashMap<>(memory: "1Mi", cpu: "1m") | new HashMap<>(memory: "50Mi", cpu: "50m") | "1Mi"          | "1m"        | "50Mi"       | "50m"
        "only cpu"          | new HashMap<>(cpu: "1m")                | new HashMap<>(cpu: "50m")                 | null           | "1m"        | null         | "50m"
        "only mem"          | new HashMap<>(memory: "1Mi")            | new HashMap<>(memory: "50Mi"            ) | "1Mi"          | null        | "50Mi"       | null
        "only reqs"         | new HashMap<>(memory: "1Mi", cpu: "1m") | null                                      | "1Mi"          | "1m"        | null         | null
        "only limits"       | null                                    | new HashMap<>(memory: "50Mi", cpu: "50m") | null           | null        | "50Mi"       | "50m"
        "integer values"    | new HashMap<>(memory: 1, cpu: 2)        | new HashMap<>(memory: 3, cpu: 4)          | "1"            | "2"         | "3"          | "4"
    }

    def "adds no requests or limits when not specified"() {
        setup:
        KubernetesContainerDescription container = new KubernetesContainerDescription()
        def service = createServiceTestDouble()
        def deploymentEnvironment = new DeploymentEnvironment()

        when:
        service.applyCustomSize(container, deploymentEnvironment, "echo")

        then:
        container.requests == null
        container.limits == null
    }

    def "no-ops when given null component"() {
        setup:
        KubernetesContainerDescription container = new KubernetesContainerDescription()
        def service = createServiceTestDouble()
        def requests = new HashMap<>(memory: "1Mi", cpu: "1m")
        def limits = new HashMap<>(memory: "50Mi", cpu: "50m")
        def deploymentEnvironment = new DeploymentEnvironment()
        deploymentEnvironment.customSizing["echo"] = new HashMap<>(requests: requests, limits: limits)

        when:
        service.applyCustomSize(container, deploymentEnvironment, null)

        then:
        container.requests == null
        container.limits == null
    }

    @Unroll
    def "imagePullSecret is set on a replicaset"() {
        setup:
        ServiceSettings serviceSettings = new ServiceSettings()
        def kubernetesSettings = serviceSettings.kubernetes
        def service = createServiceTestDouble()
        ReplicaSetBuilder replicaSetBuilder = new ReplicaSetBuilder()

        when:
        kubernetesSettings.imagePullSecrets = pullSecrets
        def imagePullSecrets = service.getImagePullSecrets(serviceSettings)
        replicaSetBuilder = replicaSetBuilder
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withImagePullSecrets(imagePullSecrets)
                .endSpec()
                .endTemplate()
                .endSpec()
        def replicaSet = replicaSetBuilder.build()

        then:
        replicaSet.spec.template.spec.imagePullSecrets == compiledPullSecret

        where:
        description         | pullSecrets          | compiledPullSecret
        "is null"           | null                 | []
        "is empty"          | []                   | []
        "is one item"       | ["item"]             | [new LocalObjectReference("item")]
        "is multiple items" | ["item1", "item2"]   | [new LocalObjectReference("item1"), new LocalObjectReference("item2")]
    }

    private KubernetesDistributedService createServiceTestDouble() {
        new KubernetesDistributedService() {
            @Override
            String getDockerRegistry(String deploymentName) {
                return null
            }

            @Override
            ArtifactService getArtifactService() {
                return null
            }

            @Override
            ServiceInterfaceFactory getServiceInterfaceFactory() {
                return null
            }

            @Override
            ObjectMapper getObjectMapper() {
                return null
            }

            void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {

            }

            @Override
            String getSpinnakerStagingPath(String deploymentName) {
                return null
            }

            @Override
            String getServiceName() {
                return null
            }

            @Override
            SpinnakerMonitoringDaemonService getMonitoringDaemonService() {
                return null
            }

            @Override
            Object connectToService(AccountDeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings, SpinnakerService sidecar) {
                return null
            }

            @Override
            Object connectToInstance(AccountDeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings, SpinnakerService sidecar, String instanceId) {
                return null
            }

            @Override
            boolean isRequiredToBootstrap() {
                return false
            }

            @Override
            DistributedService.DeployPriority getDeployPriority() {
                return null
            }

            @Override
            SpinnakerService getService() {
                return null
            }

            @Override
            ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
                return null
            }

            @Override
            ServiceSettings getDefaultServiceSettings(DeploymentConfiguration deploymentConfiguration) {
                return null
            }

            @Override
            SpinnakerArtifact getArtifact() {
                return null
            }
        }
    }
}
