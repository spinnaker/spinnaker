/*
 * Copyright 2019 Bol.com
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.halyard.config.config.v1.StrictObjectMapper
import com.netflix.spinnaker.halyard.config.model.v1.node.AffinityConfig
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration
import com.netflix.spinnaker.halyard.config.model.v1.node.SidecarConfig
import com.netflix.spinnaker.halyard.config.model.v1.node.Toleration
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.KubernetesSettings
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings
import spock.lang.Specification

class KubernetesV2ServiceTest extends Specification {

    private KubernetesV2Service testService
    private AccountDeploymentDetails details
    private GenerateService.ResolvedConfiguration config
    private ServiceSettings serviceSettings

    def setup() {
        testService = new KubernetesV2OrcaService() {
            @Override
            String getSpinnakerStagingPath(String deploymentName) {
                return "/dummy/value"
            }

            @Override
            ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
                return Mock(ServiceSettings)
            }

            @Override
            int hashCode() {
                return 42
            }

            @Override
            ObjectMapper getObjectMapper() {
                return new StrictObjectMapper();
            }
        }
        testService.serviceDelegate = new KubernetesV2ServiceDelegate()

        serviceSettings = new ServiceSettings()
        serviceSettings.env = new HashMap<String, String>()
        serviceSettings.healthEndpoint = "/health"

        config = new GenerateService.ResolvedConfiguration()
        config.runtimeSettings = Stub(SpinnakerRuntimeSettings) {
            getServiceSettings(_) >> serviceSettings
        }

        details = new AccountDeploymentDetails()
        details.account = new KubernetesAccount()
        details.deploymentConfiguration = new DeploymentConfiguration()
    }
    def "Does getSidecars work with default RuntimeSettings"() {
        setup:
        SpinnakerRuntimeSettings runtimeSettings = new SpinnakerRuntimeSettings()

        when:
        List<SidecarService> sidecar = testService.getSidecars(runtimeSettings)

        then:
        sidecar.size() == 0
    }
    def "Can we submit an empty port?"() {
        setup:
        SidecarConfig car = new SidecarConfig()
        car.setName("cloudsql-proxy")
        car.setMountPath("/cloudsql")

        when:
        String customSidecar = testService.buildCustomSidecar(car)

        then:
        customSidecar.contains('"ports": [],')
    }

    def "Does a port get converted to a containerPort?"() {
        setup:
        SidecarConfig car = new SidecarConfig()
        car.setName("cloudsql-proxy")
        car.setMountPath("/cloudsql")
        car.setPort(8080)
        when:
        String customSidecar = testService.buildCustomSidecar(car)

        then:
        customSidecar.contains('"ports": [{ "containerPort": 8080 }\n]')
    }

    def "Defaults Service.type to ClusterIP?"() {
        when:
        String yaml = testService.getServiceYaml(config)

        then:
        yaml.contains('type: ClusterIP')
    }

    def "Can we set Service.nodePort?"() {
        setup:
        serviceSettings.getKubernetes().serviceType = "NodePort"
        serviceSettings.getKubernetes().nodePort = "1234"

        when:
        String yaml = testService.getServiceYaml(config)

        then:
        yaml.contains('type: NodePort')
        yaml.contains('nodePort: 1234')
    }

    def "Can we set PodSpec.nodeSelector?"() {
        setup:
        serviceSettings.getKubernetes().nodeSelector = new HashMap<String, String>()
        serviceSettings.getKubernetes().nodeSelector["kops.k8s.io/instancegroup"] = "clouddriver"
        def executor = Mock(KubernetesV2Executor)

        when:
        String yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"kops.k8s.io/instancegroup": "clouddriver"')
    }

    def "Do SecretVolumeMounts end up being valid mountPaths?"() {
        setup:
        SidecarConfig car = new SidecarConfig()
        car.setName("cloudsql-proxy")
        car.setDockerImage("gcr.io/cloudsql-docker/gce-proxy:1.13")
        car.setCommand(["/cloud_sql_proxy"])
        car.setArgs([
                "--dir=/cloudsql",
                "-instances=instance-name:gcp-region:database-name=tcp:3306",
                "-credential_file=/secrets/cloudsql/credentials-cloudsql-instance.json",
        ])
        car.setMountPath("/cloudsql")

        SidecarConfig.SecretVolumeMount secret1 = new SidecarConfig.SecretVolumeMount()
        secret1.mountPath = "/secrets/cloudsql"
        secret1.secretName = "credentials-cloudsql-instance"

        SidecarConfig.SecretVolumeMount secret2 = new SidecarConfig.SecretVolumeMount()
        secret2.mountPath = "/var/run/secrets/kubernetes.io/serviceaccount"
        secret2.secretName = "default-token-8pndx"

        car.setSecretVolumeMounts([secret1, secret2])
        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.sidecars.put("spin-orca", [car])

        when:
        String customSidecar = testService.buildCustomSidecar(car)

        then:
        customSidecar.contains('"mountPath": "/secrets/cloudsql"')
        customSidecar.contains('"mountPath": "/var/run/secrets/kubernetes.io/serviceaccount"')
    }

    def "Does combineVolumes produce correct output"() {
        setup:
        List<ConfigSource> configSources = new ArrayList<>()
        configSources.add(new ConfigSource().setId("myid").setMountPath("mypath"))
        KubernetesSettings settings = new KubernetesSettings()
        settings.volumes.add(new ConfigSource().setId("kubid").setMountPath("kubpath"))
        List<SidecarConfig> sidecarConfigs = new ArrayList<>()
        SidecarConfig car = new SidecarConfig()
        SidecarConfig.ConfigMapVolumeMount cvm = new SidecarConfig.ConfigMapVolumeMount(configMapName: "cMap", mountPath: "/configMap")
        car.getConfigMapVolumeMounts().add(cvm)
        SidecarConfig.SecretVolumeMount svm = new SidecarConfig.SecretVolumeMount(secretName: "sMap", mountPath: "/secretMap")
        car.getSecretVolumeMounts().add(svm)
        sidecarConfigs.add(car)

        when:
        List<String> volumes = testService.combineVolumes(configSources, settings, sidecarConfigs)

        then:
        volumes.contains('''{
  "name": "myid",
  "secret": {
    "secretName": "myid"
  }
}
''')
        volumes.contains('''{
  "name": "kubid",
  "secret": {
    "secretName": "kubid"
  }
}
''')

        volumes.contains('''{
  "name": "cMap",
  "configMap": {
    "name": "cMap"
  }
}
''')

        volumes.contains('''{
  "name": "sMap",
  "secret": {
    "secretName": "sMap"
  }
}
''')
    }

    def "Can we set initContainers?"() {
        setup:
        def executor = Mock(KubernetesV2Executor)
        HashMap car = new HashMap()
        car.put("container", "gcr.io/test:latest")

        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.initContainers.put("spin-orca", [car])

        when:
        String yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"container":"gcr.io/test:latest"')
    }

    def "Can we set nodeAffinities?"() {
        setup:
        def executor = Mock(KubernetesV2Executor)
        def affinity = new AffinityConfig(
                nodeAffinity: new AffinityConfig.NodeAffinity(
                    requiredDuringSchedulingIgnoredDuringExecution: new AffinityConfig.NodeSelector(
                        nodeSelectorTerms: [new AffinityConfig.NodeSelectorTerm(
                            matchExpressions: [new AffinityConfig.NodeSelectorRequirement(
                                    key: "test_key",
                                    operator: AffinityConfig.NodeSelectorRequirement.Operator.In,
                                    values: ["test_value"]
                            )]
                        )]
                    )
                )
        )

        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.affinity.put("spin-orca", affinity)

        when:
        String yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"nodeAffinity":{"requiredDuringSchedulingIgnoredDuringExecution":{"nodeSelectorTerms":[{"matchExpressions":[{"key":"test_key","operator":"In","values":["test_value"]}]}]}}}')

        when:
        //try a weighted affinity
        def weightedSelector = new AffinityConfig.PreferredSchedulingTerm()
        weightedSelector.weight = 100
        weightedSelector.preference = affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0]

        affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution = null
        affinity.nodeAffinity.preferredDuringSchedulingIgnoredDuringExecution = [weightedSelector]
        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.affinity.put("spin-orca", affinity)

        yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"nodeAffinity":{"preferredDuringSchedulingIgnoredDuringExecution":[{"weight":100,"preference":{"matchExpressions":[{"key":"test_key","operator":"In","values":["test_value"]}]}}]}}')
    }

    def "Can we set podAffinities?"() {
        setup:
        def executor = Mock(KubernetesV2Executor)
        def affinity = new AffinityConfig(
                podAffinity: new AffinityConfig.PodAffinity(
                        requiredDuringSchedulingIgnoredDuringExecution: [new AffinityConfig.PodAffinityTerm(
                                labelSelector: new AffinityConfig.LabelSelector(
                                        matchExpressions: [new AffinityConfig.LabelSelectorRequirement(
                                                key: "test_key",
                                                operator: AffinityConfig.LabelSelectorRequirement.Operator.In,
                                                values: ["test_value"]
                                        )]
                                ),
                                namespaces: ["test_namespace"],
                                topologyKey: "failure-domain.beta.kubernetes.io/zone"
                        )]
                )
        )

        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.affinity.put("spin-orca", affinity)

        when:
        String yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"podAffinity":{"requiredDuringSchedulingIgnoredDuringExecution":[{"labelSelector":{"matchExpressions":[{"key":"test_key","operator":"In","values":["test_value"]}]},"namespaces":["test_namespace"],"topologyKey":"failure-domain.beta.kubernetes.io/zone"}]}}')

        when:
        def term = affinity.podAffinity.requiredDuringSchedulingIgnoredDuringExecution[0]
        affinity.podAffinity.preferredDuringSchedulingIgnoredDuringExecution = [new AffinityConfig.WeightedPodAffinityTerm(
                weight: 100,
                podAffinityTerm: term
        )]
        affinity.podAffinity.requiredDuringSchedulingIgnoredDuringExecution = null
        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.affinity.put("spin-orca", affinity)

        yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"podAffinity":{"preferredDuringSchedulingIgnoredDuringExecution":[{"weight":100,"podAffinityTerm":{"labelSelector":{"matchExpressions":[{"key":"test_key","operator":"In","values":["test_value"]}]},"namespaces":["test_namespace"],"topologyKey":"failure-domain.beta.kubernetes.io/zone"}}]}}')
    }

    def "Can we set podAntiAffinities?"() {
        setup:
        def executor = Mock(KubernetesV2Executor)
        def affinity = new AffinityConfig(
                podAntiAffinity: new AffinityConfig.PodAffinity(
                        requiredDuringSchedulingIgnoredDuringExecution: [new AffinityConfig.PodAffinityTerm(
                                labelSelector: new AffinityConfig.LabelSelector(
                                        matchExpressions: [new AffinityConfig.LabelSelectorRequirement(
                                                key: "test_key",
                                                operator: AffinityConfig.LabelSelectorRequirement.Operator.In,
                                                values: ["test_value"]
                                        )]
                                ),
                                namespaces: ["test_namespace"],
                                topologyKey: "failure-domain.beta.kubernetes.io/zone"
                        )]
                )
        )

        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.affinity.put("spin-orca", affinity)

        when:
        String yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"podAntiAffinity":{"requiredDuringSchedulingIgnoredDuringExecution":[{"labelSelector":{"matchExpressions":[{"key":"test_key","operator":"In","values":["test_value"]}]},"namespaces":["test_namespace"],"topologyKey":"failure-domain.beta.kubernetes.io/zone"}]}}')

        when:
        def term = affinity.podAntiAffinity.requiredDuringSchedulingIgnoredDuringExecution[0]
        affinity.podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution = [new AffinityConfig.WeightedPodAffinityTerm(
                weight: 100,
                podAffinityTerm: term
        )]
        affinity.podAntiAffinity.requiredDuringSchedulingIgnoredDuringExecution = null
        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.affinity.put("spin-orca", affinity)

        yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"podAntiAffinity":{"preferredDuringSchedulingIgnoredDuringExecution":[{"weight":100,"podAffinityTerm":{"labelSelector":{"matchExpressions":[{"key":"test_key","operator":"In","values":["test_value"]}]},"namespaces":["test_namespace"],"topologyKey":"failure-domain.beta.kubernetes.io/zone"}}]}}')
    }

    def "Can we set PodTolerations"() {
        setup:
        def executor = Mock(KubernetesV2Executor)
        def toleration = new Toleration(
                key: "test",
                value: "a",
                effect: "NoSchedule",
                operator: Toleration.Operator.Equal
        )

        details.deploymentConfiguration = new DeploymentConfiguration()
        details.deploymentConfiguration.deploymentEnvironment.tolerations.put("spin-orca", Collections.singletonList(toleration))

        when:
        String yaml = testService.getPodSpecYaml(executor, details, config)

        then:
        yaml.contains('"tolerations": [{"key":"test","operator":"Equal","value":"a","effect":"NoSchedule"}]')
    }

    def "Can we set ServiceAccountNames"() {
        setup:
        def executor = Mock(KubernetesV2Executor)
        serviceSettings.getKubernetes().serviceAccountName = "customServiceAccount"

        when:
        String podSpecYaml = testService.getPodSpecYaml(executor, details, config)

        then:
        podSpecYaml.contains('"serviceAccountName": customServiceAccount')
    }

    def "Can we use TCP probe"() {
        setup:
        def settings = new KubernetesSettings()
        settings.useTcpProbe = true
        serviceSettings.kubernetes = settings
        serviceSettings.port = 8000

        when:
        String yaml = testService.buildContainer("orca", details, serviceSettings, new ArrayList<>(), new HashMap<>())

        then:
        yaml.contains('''"readinessProbe": {
  "tcpSocket": {
    "port": 8000
  },
  "initialDelaySeconds": 
}
''')
    }

    def "Readiness probe"() {
        setup:
        def settings = new KubernetesSettings()
        serviceSettings.kubernetes = settings
        serviceSettings.port = 8000
        serviceSettings.scheme = "http"
        serviceSettings.healthEndpoint = "/health"
        if (tcpProbe != null) {
            settings.useTcpProbe = tcpProbe
        }
        if (execProbe != null) {
            settings.useExecHealthCheck = execProbe
        }

        when:
        String yaml = testService.getProbe(serviceSettings, null).toString()

        then:
        yaml.contains(readinessProbeResult)

        where:
        description         | tcpProbe   | execProbe    | readinessProbeResult
        "default"           | null       | null         | "exec"
        "tcpProbe on"       | true       | null         | "tcpSocket"
        "tcpProbe off"      | false      | null         | "exec"
        "exec probe on"     | null       | true         | "exec"
        "exec probe off"    | null      | false       | "http"
    }
}
