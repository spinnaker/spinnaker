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

package com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper

import com.google.common.collect.Lists
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.*

import static com.google.common.base.Strings.emptyToNull

class DeployDcosServerGroupDescriptionToAppMapper {
  App map(final String resolvedAppName, final DeployDcosServerGroupDescription description) {
    new App().with {
      id = resolvedAppName
      instances = description.desiredCapacity
      cpus = description.cpus
      mem = description.mem
      disk = description.disk
      gpus = description.gpus

      if (description.networkType == "USER" && emptyToNull(description.networkName)) {
        ipAddress = new AppIpAddress().with {
          networkName = description.networkName
          it
        }
      }

      container = new Container().with {
        // We are using a ternary instead of a safe navigation to avoid setting these fields with empty
        // collections because of the potential issue they may cause with being read by marathon.
        // This is done throughout this mapper regardless of whether that field is known to cause issues
        // just to play it safe and to be consistent as much as possible.
        docker = description.docker ? new Docker().with {
          image = description.docker.image.imageId
          network = description.networkType

          if ("HOST" != description.networkType) {
            portMappings = parsePortMappings(resolvedAppName, description.serviceEndpoints)
          }
          privileged = description.docker.privileged
          parameters = description.docker.parameters?.entrySet()?.collect({ entry ->
            new Parameter().with {
              key = entry.key
              value = entry.value
              it
            }
          }) as Collection<Parameter>
          forcePullImage = description.docker.forcePullImage

          it
        } : null
        volumes = parseVolumes(description.persistentVolumes, description.dockerVolumes, description.externalVolumes)
        type = volumes ? "DOCKER" : null

        it
      }

      env = description.env
      user = description.dcosUser
      cmd = description.cmd
      args = description.args
      constraints = parseConstraints(description.constraints)

      fetch = description.fetch ? description.fetch.collect({ fetchable ->
        new Fetchable().with {
          uri = fetchable.uri
          cache = fetchable.cache
          extract = fetchable.extract
          executable = fetchable.executable
          outputFile = fetchable.outputFile
          it
        }
      }) as List<Fetchable> : null

      storeUrls = description.storeUrls
      backoffSeconds = description.backoffSeconds
      backoffFactor = description.backoffFactor
      maxLaunchDelaySeconds = description.maxLaunchDelaySeconds

      healthChecks = description.healthChecks ? description.healthChecks.collect({ healthCheck ->
        new HealthCheck().with {
          if (emptyToNull(healthCheck.command?.trim()) != null) {
            command = new Command(value: healthCheck.command)
          }

          gracePeriodSeconds = healthCheck.gracePeriodSeconds
          ignoreHttp1xx = healthCheck.ignoreHttp1xx
          intervalSeconds = healthCheck.intervalSeconds
          maxConsecutiveFailures = healthCheck.maxConsecutiveFailures
          path = healthCheck.path
          port = healthCheck.port
          portIndex = healthCheck.portIndex
          protocol = healthCheck.protocol
          timeoutSeconds = healthCheck.timeoutSeconds
          it
        }
      }) as List<HealthCheck> : null

      readinessChecks = description.readinessChecks ? description.readinessChecks.collect({ readinessCheck ->
        new ReadinessCheck().with {
          name = readinessCheck.name
          protocol = readinessCheck.protocol
          path = readinessCheck.path
          portName = readinessCheck.portName
          intervalSeconds = readinessCheck.intervalSeconds
          timeoutSeconds = readinessCheck.timeoutSeconds
          httpStatusCodesForReady = readinessCheck.httpStatusCodesForReady
          preserveLastResponse = readinessCheck.preserveLastResponse
          it
        }
      }) as List<ReadinessCheck> : null

      dependencies = description.dependencies ? description.dependencies : null
      labels = description.labels ? description.labels : null

      residency = description.residency ? new Residency().with {
        taskLostBehaviour = description.residency.taskLostBehaviour
        relaunchEscalationTimeoutSeconds = description.residency.relaunchEscalationTimeoutSeconds
        it
      } : null

      taskKillGracePeriodSeconds = description.taskKillGracePeriodSeconds
      secrets = description.secrets
      requirePorts = description.requirePorts
      acceptedResourceRoles = description.acceptedResourceRoles

      if ("HOST" == description.networkType) {
        portDefinitions = parsePortDefinitions(resolvedAppName, description.serviceEndpoints)
      }

      if (description.upgradeStrategy) {
        upgradeStrategy = new UpgradeStrategy().with {
          maximumOverCapacity = description.upgradeStrategy.maximumOverCapacity
          minimumHealthCapacity = description.upgradeStrategy.minimumHealthCapacity
          it
        }
      }

      it
    }
  }

  private static List<List<String>> parseConstraints(String constaints) {
    def parsedConstraints = new ArrayList<List<String>>()

    if (constaints == null || constaints.trim().isEmpty()) {
      return parsedConstraints
    }

    List<String> constraintGroups = constaints.split(',')

    if (constraintGroups.isEmpty()) {
      return parsedConstraints
    }

    constraintGroups.forEach({
      constraintGroup -> parsedConstraints.add(Lists.newArrayList(constraintGroup.split(':')))
    })

    parsedConstraints.forEach({
      constraintGroup ->
        if (constraintGroup.size() < 2 || constraintGroup.size() > 3) {
          throw new RuntimeException("Given constraint [${constraintGroup.join(':')}] was invalid as it had ${constraintGroup.size()} parts instead of the expected 2 or 3.")
        }
    })

    parsedConstraints ? parsedConstraints : null
  }

  private
  static Map<String, String> parsePortMappingLabels(String appId, DeployDcosServerGroupDescription.ServiceEndpoint serviceEndpoint, int index) {
    def parsedLabels = (Map<String, String>) serviceEndpoint.labels.clone()

    if (serviceEndpoint.loadBalanced && !parsedLabels.containsKey("VIP_${index}".toString())) {
      parsedLabels.put("VIP_${index}".toString(), "${appId}:${serviceEndpoint.port}".toString())
    }

    parsedLabels ? parsedLabels : null
  }

  private
  static List<PortMapping> parsePortMappings(String appId, List<DeployDcosServerGroupDescription.ServiceEndpoint> serviceEndpoints) {
    def portMapping = serviceEndpoints.withIndex().collect({
      serviceEndpoint, index ->
        new PortMapping().with {
          name = serviceEndpoint.name
          protocol = serviceEndpoint.protocol
          containerPort = serviceEndpoint.port
          servicePort = serviceEndpoint.servicePort
          labels = parsePortMappingLabels(appId, serviceEndpoint, index)
          it
        }
    }) as List<PortMapping>

    portMapping ? portMapping : null
  }

  private
  static List<PortDefinition> parsePortDefinitions(String appId, List<DeployDcosServerGroupDescription.ServiceEndpoint> serviceEndpoints) {
    def portDefinitions = serviceEndpoints.withIndex().collect({
      serviceEndpoint, index ->
        new PortDefinition().with {
          name = serviceEndpoint.name
          protocol = serviceEndpoint.protocol
          labels = parsePortMappingLabels(appId, serviceEndpoint, index)
          port = serviceEndpoint.port
          it
        }
    })

    portDefinitions ? portDefinitions : null
  }

  private static List<Volume> parseVolumes(List<DeployDcosServerGroupDescription.PersistentVolume> persistentVolumes,
                                           List<DeployDcosServerGroupDescription.DockerVolume> dockerVolumes,
                                           List<DeployDcosServerGroupDescription.ExternalVolume> externalVolumes) {
    def parsedVolumes = new ArrayList<Volume>()

    persistentVolumes.forEach({
      persistentVolume ->
        parsedVolumes.add(new PersistentLocalVolume().with {
          it.setPersistentLocalVolumeInfo(new PersistentLocalVolume.PersistentLocalVolumeInfo().with {
            size = persistentVolume.persistent.size
            it
          })
          containerPath = persistentVolume.containerPath
          mode = persistentVolume.mode
          it
        })
    })

    dockerVolumes.forEach({
      dockerVolume ->
        parsedVolumes.add(new LocalVolume().with {
          hostPath = dockerVolume.hostPath
          containerPath = dockerVolume.containerPath
          mode = dockerVolume.mode
          it
        })
    })

    externalVolumes.forEach({
      externalVolume ->
        parsedVolumes.add(new ExternalVolume().with {
          containerPath = externalVolume.containerPath
          mode = externalVolume.mode

          if (externalVolume.external) {
            name = externalVolume.external.name
            provider = externalVolume.external.provider
            size = externalVolume.external.size

            if (externalVolume.external.options) {
              driver = externalVolume.external.options.driver
              optSize = externalVolume.external.options.size
              optIops = externalVolume.external.options.iops
              optVolumeType = externalVolume.external.options.volumeType
              optNewFsType = externalVolume.external.options.newFsType
              optOverwriteFs = externalVolume.external.options.overwriteFs
            }
          }

          it
        })
    })

    parsedVolumes ? parsedVolumes : null
  }
}
