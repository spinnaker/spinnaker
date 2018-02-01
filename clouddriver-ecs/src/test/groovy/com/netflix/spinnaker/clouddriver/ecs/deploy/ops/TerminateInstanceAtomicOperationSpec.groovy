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
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription

class TerminateInstanceAtomicOperationSpec extends CommonAtomicOperation {
  void 'should execute the operation'() {
    given:
    def operation = new TerminateInstancesAtomicOperation(new TerminateInstancesDescription(
      credentials: TestCredential.named('Test', [:]),
      ecsTaskIds: ['deadbeef-1111-4637-ab84-606f0c77af42', 'deadbeef-2222-4637-ab84-606f0c77af42']
    ))

    operation.amazonClientProvider = amazonClientProvider
    operation.accountCredentialsProvider = accountCredentialsProvider
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    containerInformationService.getClusterArn(_, _, _) >> 'cluster-arn'
    accountCredentialsProvider.getCredentials(_) >> TestCredential.named("test")

    when:
    operation.operate([])

    then:
    2 * ecs.stopTask(_)
  }
}
