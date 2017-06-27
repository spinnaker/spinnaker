package com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import mesosphere.marathon.client.model.v2.App
import spock.lang.Specification

class DeployDcosServerGroupDescriptionToAppMapperSpec extends Specification {
    private static final APPLICATION_NAME = DcosSpinnakerAppId.fromVerbose("spinnaker", "test", "api-test-something-v000").get()

    void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully'() {

        given:
        DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
                application: APPLICATION_NAME.serverGroupName.app, stack: APPLICATION_NAME.serverGroupName.stack,
                freeFormDetails: APPLICATION_NAME.serverGroupName.detail, desiredCapacity: 1, cpus: 1.0, mem: 1.0, gpus: 1.0,
                disk: 0.0, env: ["var": "val"], dcosUser: 'spinnaker', cmd: 'ps', args: ["-A"],
                constraints: "something:GROUP_BY:other,test:GROUP_BY:other", fetch: [new DeployDcosServerGroupDescription.Fetchable(uri: "uri", executable: true, extract: true, cache: true, outputFile: "file")],
                storeUrls: [ "someUrl" ], backoffSeconds: 1, backoffFactor: 1.15, maxLaunchDelaySeconds: 3600,
                dependencies: ["some-other-service-v000"], labels: ["key": "value"],
                residency: new DeployDcosServerGroupDescription.Residency(taskLostBehaviour: "idk", relaunchEscalationTimeoutSeconds: 0),
                taskKillGracePeriodSeconds: 1, secrets: [ "secret": "this is super secret"], requirePorts: false,
                acceptedResourceRoles: ["slave_public"],
                dockerVolumes: [new DeployDcosServerGroupDescription.DockerVolume(containerPath: "path/to/container",
                    hostPath: "host/path/to/container", mode: "someMode")],
                externalVolumes: [new DeployDcosServerGroupDescription.ExternalVolume(
                        external: new DeployDcosServerGroupDescription.ExternalStorage(name: "lkjlj", provider: "dvdi",
                                options: new DeployDcosServerGroupDescription.ExternalStorageOptions(driver: "rexray",
                                        size: 1, iops: 1, volumeType: "something", newFsType: "other", overwriteFs: true)),
                        mode: "someMode")],
                persistentVolumes: [new DeployDcosServerGroupDescription.PersistentVolume(containerPath: "path/to/container",
                        persistent: new DeployDcosServerGroupDescription.PersistentStorage(size: 512), mode: "someMode")],
                networkType: "BRIDGE",
                docker: new DeployDcosServerGroupDescription.Docker(privileged: false, forcePullImage: true,
                        network: "BRIDGE",
                        image: new DeployDcosServerGroupDescription.Image(imageId: "some/image:latest"),
                        parameters: ["param": "value"]),
                readinessChecks: [new DeployDcosServerGroupDescription.ReadinessCheck(name: 'check', protocol: 'tcp',
                        portName: 'checkPort', path: '/meta/health', intervalSeconds: 30, timeoutSeconds: 270,
                        httpStatusCodesForReady: [200, 201], preserveLastResponse: false)],
                healthChecks: [new DeployDcosServerGroupDescription.HealthCheck(path: "/meta/health", protocol: "tcp",
                        portIndex: 8080, gracePeriodSeconds: 5, intervalSeconds: 30, maxConsecutiveFailures: 1,
                        ignoreHttp1xx: false)],
                serviceEndpoints: [new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8080, protocol: "tcp",
                        networkType: "BRIDGE",
                        name: "HTTP", loadBalanced: false, exposeToHost: false),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8081, protocol: "tcp",
                        networkType: "BRIDGE",
                        name: "HTTP", loadBalanced: true, exposeToHost: false),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8082, protocol: "tcp",
                        networkType: "BRIDGE",
                        name: "HTTP", loadBalanced: true, exposeToHost: false, labels: ["VIP_2": "vip_override:8082"]),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8083, protocol: "tcp",
                        networkType: "BRIDGE",
                        name: "HTTP", loadBalanced: false, exposeToHost: false, labels: ["label": "non_vip_label"]),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8084, protocol: "tcp",
                        networkType: "BRIDGE",
                        name: "HTTP", loadBalanced: true, exposeToHost: false, labels: ["VIP_10": "vip_override:8084"])],
                upgradeStrategy: new DeployDcosServerGroupDescription.UpgradeStrategy(minimumHealthCapacity: 1,
                        maximumOverCapacity: 2))

        when:
        App app = new DeployDcosServerGroupDescriptionToAppMapper().map(APPLICATION_NAME.toString(), description)

        then:
        noExceptionThrown()
        app.instances == description.desiredCapacity
        app.cpus == description.cpus
        app.mem == description.mem
        app.gpus == description.gpus
        app.disk == description.disk
        app.env == description.env
        app.cmd == description.cmd
        app.args == description.args
        app.user == description.dcosUser

        app.fetch.size() == description.fetch.size()
        [app.fetch, description.fetch].transpose().forEach({ appFetch, descriptionFetch ->
            assert appFetch.uri == descriptionFetch.uri
            assert appFetch.cache == descriptionFetch.cache
            assert appFetch.extract == descriptionFetch.extract
            assert appFetch.executable == descriptionFetch.executable
            assert appFetch.outputFile == descriptionFetch.outputFile
        })

        app.storeUrls == description.storeUrls
        app.backoffSeconds == description.backoffSeconds
        app.backoffFactor == description.backoffFactor
        app.maxLaunchDelaySeconds == description.maxLaunchDelaySeconds

        app.readinessChecks.size() == description.readinessChecks.size()
        [app.readinessChecks, description.readinessChecks].transpose().forEach({ appReadinessCheck, descriptionReadinessCheck ->
            assert appReadinessCheck.name == descriptionReadinessCheck.name
            assert appReadinessCheck.protocol == descriptionReadinessCheck.protocol
            assert appReadinessCheck.path == descriptionReadinessCheck.path
            assert appReadinessCheck.portName == descriptionReadinessCheck.portName
            assert appReadinessCheck.intervalSeconds == descriptionReadinessCheck.intervalSeconds
            assert appReadinessCheck.timeoutSeconds == descriptionReadinessCheck.timeoutSeconds
            assert appReadinessCheck.httpStatusCodesForReady == descriptionReadinessCheck.httpStatusCodesForReady
            assert appReadinessCheck.preserveLastResponse == descriptionReadinessCheck.preserveLastResponse
        })

        app.dependencies == description.dependencies
        app.labels == description.labels
        app.version == null

        if (app.residency && description.residency) {
            assert app.residency.taskLostBehaviour == description.residency.taskLostBehaviour
            assert app.residency.relaunchEscalationTimeoutSeconds == description.residency.relaunchEscalationTimeoutSeconds
        }

        app.taskKillGracePeriodSeconds == description.taskKillGracePeriodSeconds
        app.secrets == description.secrets
        app.requirePorts == description.requirePorts
        app.acceptedResourceRoles == description.acceptedResourceRoles
        app.container.docker.image == description.docker.image.imageId
        app.container.docker.network == description.docker.network
        app.container.docker.privileged == description.docker.privileged
        app.container.docker.forcePullImage == description.docker.forcePullImage

        def labels = [null,
                      ["VIP_1":"/spinnaker/test/api-test-something-v000:8081"],
                      ["VIP_2":"vip_override:8082"],
                      ["label":"non_vip_label"],
                      ["VIP_4":"/spinnaker/test/api-test-something-v000:8084", "VIP_10":"vip_override:8084"]]
        app.container.docker.portMappings.size() == description.serviceEndpoints.size()
        [app.container.docker.portMappings, description.serviceEndpoints, labels].transpose().forEach({ appPortMapping, descriptionPortMapping, label ->
            assert appPortMapping.containerPort == descriptionPortMapping.port
            assert appPortMapping.protocol == descriptionPortMapping.protocol
            assert appPortMapping.labels == label
        })

        app.container.docker.parameters.size() == description.docker.parameters.size()
        [app.container.docker.parameters, description.docker.parameters.entrySet().asList()].transpose().forEach({ appParameter, descriptionParameter ->
            assert appParameter.key == descriptionParameter.key
            assert appParameter.value == descriptionParameter.value
        })

        def combinedVolumes = []
        combinedVolumes.addAll(description.persistentVolumes)
        combinedVolumes.addAll(description.dockerVolumes)
        combinedVolumes.addAll(description.externalVolumes)

        app.container.volumes.size() == combinedVolumes.size()
        [app.container.volumes, combinedVolumes].transpose().forEach({ appVolume, descriptionVolume ->
            assert appVolume.containerPath == descriptionVolume.containerPath
            assert appVolume.mode == descriptionVolume.mode
        })

        app.healthChecks.size() == description.healthChecks.size()
        [app.healthChecks, description.healthChecks].transpose().forEach({ appHealthChecks, descriptionHealthChecks ->
            assert appHealthChecks.command?.value == descriptionHealthChecks.command
            assert appHealthChecks.gracePeriodSeconds == descriptionHealthChecks.gracePeriodSeconds
            assert appHealthChecks.ignoreHttp1xx == descriptionHealthChecks.ignoreHttp1xx
            assert appHealthChecks.intervalSeconds == descriptionHealthChecks.intervalSeconds
            assert appHealthChecks.maxConsecutiveFailures == descriptionHealthChecks.maxConsecutiveFailures
            assert appHealthChecks.path == descriptionHealthChecks.path
            assert appHealthChecks.portIndex == descriptionHealthChecks.portIndex
            assert appHealthChecks.protocol == descriptionHealthChecks.protocol
            assert appHealthChecks.timeoutSeconds == descriptionHealthChecks.timeoutSeconds
        })

        app.portDefinitions == null
//        app.portDefinitions.size() == description.serviceEndpoints.size()
//        [app.portDefinitions, description.serviceEndpoints].transpose().forEach({ appPortDefinition, descriptionPortDefinition ->
//            assert appPortDefinition.protocol == descriptionPortDefinition.protocol
//            assert appPortDefinition.port == descriptionPortDefinition.port
//        })

        if (app.upgradeStrategy && description.upgradeStrategy) {
            assert app.upgradeStrategy.maximumOverCapacity == description.upgradeStrategy.maximumOverCapacity
            assert app.upgradeStrategy.minimumHealthCapacity == description.upgradeStrategy.minimumHealthCapacity
        }
    }

    void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully with many fields left empty'() {
        given:
        DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
                application: APPLICATION_NAME.serverGroupName.app, stack: APPLICATION_NAME.serverGroupName.stack,
                freeFormDetails: APPLICATION_NAME.serverGroupName.detail, desiredCapacity: 1, cpus: 1.0, mem: 1.0, gpus: 1.0, disk: 0.0)

        when:
        App app = new DeployDcosServerGroupDescriptionToAppMapper().map(APPLICATION_NAME.toString(), description)

        then:
        noExceptionThrown()
        app.instances == description.desiredCapacity
        app.cpus == description.cpus
        app.mem == description.mem
        app.gpus == description.gpus
        app.disk == description.disk
        app.env == description.env
        app.cmd == description.cmd
        app.args == description.args
        app.user == description.dcosUser
        app.fetch == null && description.fetch.empty
        app.storeUrls == description.storeUrls
        app.backoffSeconds == description.backoffSeconds
        app.backoffFactor == description.backoffFactor
        app.maxLaunchDelaySeconds == description.maxLaunchDelaySeconds
        app.readinessChecks == null && description.readinessChecks.empty
        app.dependencies == null && description.dependencies.empty
        app.labels == null && description.labels.isEmpty()
        app.version == null
        app.residency == null && description.residency == null
        app.taskKillGracePeriodSeconds == description.taskKillGracePeriodSeconds
        app.secrets == description.secrets
        app.requirePorts == description.requirePorts
        app.acceptedResourceRoles == description.acceptedResourceRoles
        app.container.docker == null && description.docker == null
        app.container.volumes == null && description.dockerVolumes.isEmpty() && description.externalVolumes.isEmpty() && description.persistentVolumes.isEmpty()
        app.healthChecks == null && description.healthChecks.isEmpty()
        app.portDefinitions == null && description.serviceEndpoints.isEmpty()
        app.upgradeStrategy == null && description.upgradeStrategy == null
    }
}
