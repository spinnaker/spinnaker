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

import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription

class DisableServiceAtomicOperationSpec extends CommonAtomicOperation {
  void 'should execute the operation'() {
    given:
    def operation = new DisableServiceAtomicOperation(new ModifyServiceDescription(
      serverGroupName: "test-server-group",
      credentials: TestCredential.named('Test', [:])
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.accountCredentialsProvider = accountCredentialsProvider
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    amazonClientProvider.getAmazonApplicationAutoScaling(_, _, _) >> autoscaling
    containerInformationService.getClusterName(_, _, _) >> 'cluster-name'
    accountCredentialsProvider.getCredentials(_) >> TestCredential.named("test")

    when:
    operation.operate([])

    then:
    1 * autoscaling.registerScalableTarget(_)
    1 * ecs.updateService(_)
  }
}
