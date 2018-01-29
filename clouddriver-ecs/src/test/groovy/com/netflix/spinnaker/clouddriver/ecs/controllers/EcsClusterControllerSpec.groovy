/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers

import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsClusterProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsClusterControllerSpec extends Specification {

  def provider = Mock(EcsClusterProvider)
  @Subject
  def controller = new EcsClusterController(provider)

  def 'should retrieve a collection of ECS clusters'() {
    given:
    def numberOfClusters = 5
    def givenClusters = []
    for (int x = 0; x < numberOfClusters; x++) {
      givenClusters << new EcsCluster(
        account: 'test-account-' + x,
        region: 'us-west-' + x,
        name: 'cluster-name-' + x,
        arn: 'cluster-arn-' + x
      )
    }
    provider.allEcsClusters >> givenClusters


    when:
    def retrievedClusters = controller.getAllEcsClusters()

    then:
    retrievedClusters == givenClusters
  }

}
