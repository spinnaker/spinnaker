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
import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.MachineType
import com.google.api.services.compute.model.Network
import com.google.api.services.replicapool.model.NewDisk
import com.google.api.services.replicapool.model.NewDiskInitializeParams
import com.netflix.spinnaker.kato.data.task.Task

class GCEUtil {
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

  static AttachedDisk buildAttachedDisk(Image sourceImage, String diskType) {
    def attachedDiskInitializeParams = new AttachedDiskInitializeParams(sourceImage: sourceImage.selfLink)

    return new AttachedDisk(boot: true, autoDelete: true, type: "PERSISTENT", initializeParams: attachedDiskInitializeParams)
  }

  static NewDisk buildNewDisk(Image sourceImage, long diskSizeGb) {
    def newDiskInitializeParams = new NewDiskInitializeParams(sourceImage: sourceImage.selfLink, diskSizeGb: diskSizeGb)

    return new NewDisk(boot: true, initializeParams: newDiskInitializeParams)
  }

  // There are 2 distinct types named NetworkInterface since the Replica Pool logic is in limited preview.
  // TODO(duftler): Reconcile this once com.google.apis:google-api-services-compute includes everything.
  static com.google.api.services.compute.model.NetworkInterface buildNetworkInterface(Network network,
                                                                                      String accessConfigType) {
    def accessConfig = new com.google.api.services.compute.model.AccessConfig(type: accessConfigType)

    return new com.google.api.services.compute.model.NetworkInterface(network: network.selfLink, accessConfigs: [accessConfig])
  }

  // There are 2 distinct types named NetworkInterface since the Replica Pool logic is in limited preview.
  // TODO(duftler): Reconcile this once com.google.apis:google-api-services-compute includes everything.
  static com.google.api.services.replicapool.model.NetworkInterface buildNetworkInterface(String networkName,
                                                                                          String accessConfigName,
                                                                                          String accessConfigType) {
    def accessConfig = new com.google.api.services.replicapool.model.AccessConfig(name: accessConfigName, type: accessConfigType)

    return new com.google.api.services.replicapool.model.NetworkInterface(network: networkName, accessConfigs: [accessConfig])
  }

  private static void updateStatusAndThrowException(String errorMsg, Task task, String phase) {
    task.updateStatus phase, errorMsg
    throw new GCEResourceNotFoundException(errorMsg)
  }
}
