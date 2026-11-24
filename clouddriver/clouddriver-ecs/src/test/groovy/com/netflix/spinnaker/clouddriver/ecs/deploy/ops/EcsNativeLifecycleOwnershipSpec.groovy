/*
 * Copyright 2026 Spinnaker contributors.
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

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.ecs.model.Tag

class EcsNativeLifecycleOwnershipSpec extends CommonAtomicOperation {

  void 'rejects an unowned native disable before any write'() {
    given:
    def operation = new DisableServiceAtomicOperation(description(), true)
    inject(operation)
    unownedService()

    when:
    operation.operate([])

    then:
    thrown(IllegalStateException)
    1 * ecs.describeServices(_)
    0 * ecs.updateService(_)
    0 * autoscaling._
  }

  void 'rejects an unowned native enable before any write'() {
    given:
    def operation = new EnableServiceAtomicOperation(description(), true)
    inject(operation)
    unownedService()

    when:
    operation.operate([])

    then:
    thrown(IllegalStateException)
    1 * ecs.describeServices(_)
    0 * ecs.updateService(_)
    0 * autoscaling._
  }

  void 'rejects an unowned native resize before any write'() {
    given:
    def operation = new ResizeServiceAtomicOperation(
        new ResizeServiceDescription(
            credentials: TestCredential.named('test'),
            account: 'test',
            region: 'us-west-1',
            serverGroupName: 'external-service',
            capacity: new ServerGroup.Capacity(1, 2, 1)), true)
    inject(operation)
    unownedService()

    when:
    operation.operate([])

    then:
    thrown(IllegalStateException)
    1 * ecs.describeServices(_)
    0 * ecs.updateService(_)
    0 * autoscaling._
  }

  void 'rejects an unowned native destroy before any write'() {
    given:
    def operation = new DestroyServiceAtomicOperation(description(), true)
    inject(operation)
    unownedService()

    when:
    operation.operate([])

    then:
    thrown(IllegalStateException)
    1 * ecs.describeServices(_)
    0 * ecs.updateService(_)
    0 * ecs.deleteService(_)
    0 * ecs.deregisterTaskDefinition(_)
    0 * autoscaling._
  }

  private ModifyServiceDescription description() {
    new ModifyServiceDescription(
        credentials: TestCredential.named('test'),
        account: 'test',
        region: 'us-west-1',
        serverGroupName: 'external-service')
  }

  private void inject(AbstractEcsAtomicOperation operation) {
    operation.amazonClientProvider = amazonClientProvider
    operation.credentialsRepository = credentialsRepository
    operation.containerInformationService = containerInformationService
    amazonClientProvider.getAmazonEcsV2(_, _) >> ecs
    containerInformationService.getClusterName(_, _, _) >> 'my-cluster'
  }

  private void unownedService() {
    ecs.describeServices(_) >> DescribeServicesResponse.builder()
        .services(Service.builder()
            .serviceName('external-service')
            .tags(Tag.builder().key(EcsNativeServiceTag.KEY).value('external').build())
            .build())
        .build()
  }
}
