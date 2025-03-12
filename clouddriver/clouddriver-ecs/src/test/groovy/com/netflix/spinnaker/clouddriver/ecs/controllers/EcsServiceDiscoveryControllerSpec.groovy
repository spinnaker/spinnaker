/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.controllers


import com.netflix.spinnaker.clouddriver.ecs.cache.model.ServiceDiscoveryRegistry
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsServiceDiscoveryProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsServiceDiscoveryControllerSpec extends Specification {

  def provider = Mock(EcsServiceDiscoveryProvider)
  @Subject
  def controller = new EcsServiceDiscoveryController(provider)

  def 'should retrieve a collection of service discovery registries'() {
    given:
    def numberOfServices = 5
    def givenServices = []
    for (int x = 0; x < numberOfServices; x++) {
      givenServices << new ServiceDiscoveryRegistry(
        account: 'test-account-' + x,
        region: 'us-west-' + x,
        name: 'service-name-' + x,
        arn: 'service-arn-' + x,
        id: 'srv-' + x
      )
    }
    provider.allServiceDiscoveryRegistries >> givenServices


    when:
    def retrievedServices = controller.getAllServiceDiscoveryRegistries()

    then:
    retrievedServices == givenServices
  }

}
