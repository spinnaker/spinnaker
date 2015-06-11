/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.model.NetworkInterface
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.CreateNetworkInterfaceDescription
import com.netflix.spinnaker.kato.aws.model.ResultByZone
import com.netflix.spinnaker.kato.aws.model.TagsNotCreatedException
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.aws.services.NetworkInterfaceService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired

class CreateNetworkInterfaceAtomicOperation implements AtomicOperation<ResultByZone<NetworkInterface>> {
  private static final String BASE_PHASE = "CREATE_NETWORK_INTERFACE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  final CreateNetworkInterfaceDescription description

  CreateNetworkInterfaceAtomicOperation(CreateNetworkInterfaceDescription description) {
    this.description = description
  }

  @Override
  ResultByZone<NetworkInterface> operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Create Network Interface Operation..."
    def resultByZone = new ResultByZone.Builder<NetworkInterface>()
    for (String region : description.availabilityZonesGroupedByRegion.keySet()) {
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      NetworkInterfaceService networkInterfaceService = regionScopedProvider.networkInterfaceService
      for (String availabilityZone : description.availabilityZonesGroupedByRegion[region]) {
        try {
          def networkInterface = networkInterfaceService.createNetworkInterface(availabilityZone, description.subnetType, description.networkInterface)
          resultByZone.addSuccessfulResult(availabilityZone, networkInterface)
        } catch (Exception exception) {
          if (exception instanceof TagsNotCreatedException) {
            TagsNotCreatedException<NetworkInterface> tagsNotCreatedException = (TagsNotCreatedException) exception
            task.updateStatus BASE_PHASE, "${tagsNotCreatedException.message}"
            resultByZone.addSuccessfulResult(availabilityZone, tagsNotCreatedException.objectToTag)
          } else {
            task.updateStatus BASE_PHASE, "Failed to create Network Interface"
            resultByZone.addFailure(availabilityZone, exception)
          }
        }
      }
    }
    resultByZone.build()
  }

}
