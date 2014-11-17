/*
 * Copyright 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.deploy.gce

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.deploy.gce.ops.ReplicaPoolBuilder
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials

class GCEUtil {
  // TODO(duftler): Add support for ubuntu image project.
  // TODO(duftler): This list should not be static, but should also not be built on each call.
  static final List<String> baseImageProjects = ["centos-cloud", "coreos-cloud", "debian-cloud", "google-containers",
                                                 "opensuse-cloud", "rhel-cloud", "suse-cloud"]

  static MachineType queryMachineType(String projectName, String zone, String machineTypeName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up machine type $machineTypeName..."
    def machineType = compute.machineTypes().list(projectName, zone).execute().getItems().find {
      it.getName() == machineTypeName
    }

    if (machineType) {
      return machineType
    } else {
      updateStatusAndThrowException("Machine type $machineTypeName not found.", task, phase)
    }
  }

  static Image querySourceImage(String projectName, String sourceImageName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up source image $sourceImageName..."

    def imageProjects = [projectName] + baseImageProjects
    def sourceImage = null

    for (imageProject in imageProjects) {
      sourceImage = compute.images().list(imageProject).execute().getItems().find {
        it.getName() == sourceImageName
      }

      if (sourceImage != null) {
        return sourceImage;
      }
    }

    updateStatusAndThrowException("Source image $sourceImageName not found in any of these projects: $imageProjects.", task, phase)
  }

  static Network queryNetwork(String projectName, String networkName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up network $networkName..."
    def network = compute.networks().list(projectName).execute().getItems().find {
      it.getName() == networkName
    }

    if (network) {
      return network
    } else {
      updateStatusAndThrowException("Network $networkName not found.", task, phase)
    }
  }

  static List<InstanceGroupManager> queryManagedInstanceGroups(String projectName,
                                                               String region,
                                                               GoogleCredentials credentials,
                                                               ReplicaPoolBuilder replicaPoolBuilder,
                                                               String applicationName) {
    def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, applicationName);
    def zones = getZonesFromRegion(projectName, region, credentials.compute)

    def allMIGSInRegion = zones.findResults {
      def localZoneName = getLocalName(it)

      replicapool.instanceGroupManagers().list(projectName, localZoneName).execute().getItems()
    }.flatten()

    allMIGSInRegion
  }

  static String getRegionFromZone(String projectName, String zone, Compute compute) {
    // Zone.getRegion() returns a full URL reference.
    def fullRegion = compute.zones().get(projectName, zone).execute().getRegion()
    // Even if getRegion() is changed to return just the unqualified region name, this will still work.
    getLocalName(fullRegion)
  }

  static List<String> getZonesFromRegion(String projectName, String region, Compute compute) {
    return compute.regions().get(projectName, region).execute().getZones()
  }

  static AttachedDisk buildAttachedDisk(Image sourceImage, long diskSizeGb, String diskType) {
    def attachedDiskInitializeParams = new AttachedDiskInitializeParams(sourceImage: sourceImage.selfLink,
                                                                        diskSizeGb: diskSizeGb,)

    return new AttachedDisk(boot: true, autoDelete: true, type: diskType, initializeParams: attachedDiskInitializeParams)
  }

  static NetworkInterface buildNetworkInterface(Network network, String accessConfigName, String accessConfigType) {
    def accessConfig = new AccessConfig(name: accessConfigName, type: accessConfigType)

    return new NetworkInterface(network: network.selfLink, accessConfigs: [accessConfig])
  }

  private static void updateStatusAndThrowException(String errorMsg, Task task, String phase) {
    task.updateStatus phase, errorMsg
    throw new GCEResourceNotFoundException(errorMsg)
  }

  private static String getLocalName(String fullUrl) {
    def urlParts = fullUrl.split("/")

    return urlParts[urlParts.length - 1]
  }
}
