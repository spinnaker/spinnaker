/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup

class ResizeServiceAtomicOperationSpec extends CommonAtomicOperation {
  void 'should execute the operation'() {
    given:
    def autoscaling = Mock(ApplicationAutoScalingClient)
    def serviceName = 'myapp-kcats-liated-v007'
    def credentials = TestCredential.named('test', [:])

    def operation = new ResizeServiceAtomicOperation(new ResizeServiceDescription(
      credentials: credentials,
      serverGroupName: serviceName,
      capacity: new ServerGroup.Capacity(1, 2, 1)
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    amazonClientProvider.getAmazonApplicationAutoScalingV2(_, _) >> autoscaling
    containerInformationService.getClusterArn(_, _, _) >> 'cluster-arn'
    credentialsRepository.getOne(_) >> credentials

    when:
    operation.operate([])

    then:
    1 * ecs.updateService(_) >> UpdateServiceResponse.builder()
      .service(Service.builder().serviceName(serviceName).build())
      .build()
    1 * autoscaling.registerScalableTarget(_)
  }
}
