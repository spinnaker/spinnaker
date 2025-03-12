/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.deploy

import spock.lang.Specification;

class DeploymentResultSpec extends Specification {

  def "should no-op if `deployments` is non-null"() {
    given:
    def deploymentResult = new DeploymentResult(
        deployments: [new DeploymentResult.Deployment()],
        serverGroupNameByRegion: ["us-east-1": "app-v001"]
    )

    expect:
    deploymentResult.normalize().deployments.size() == 1
  }

  def "should normalize `serverGroupNameByRegion` and `deployedNamesByLocation`"() {
    given:
    def deploymentResult = new DeploymentResult(
        serverGroupNameByRegion: ["us-east-1": "app-v001"],
        deployedNamesByLocation: ["us-west-2": ["app-v002", "app-v003"]]
    )

    when:
    deploymentResult.normalize()

    then:
    (deploymentResult.deployments as List) == [
        new DeploymentResult.Deployment(location: "us-east-1", serverGroupName: "app-v001"),
        new DeploymentResult.Deployment(location: "us-west-2", serverGroupName: "app-v002"),
        new DeploymentResult.Deployment(location: "us-west-2", serverGroupName: "app-v003")
    ]
  }
}
