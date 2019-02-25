/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CloudFoundryWaitForDestroyServiceTaskTest extends Specification {
  @Subject task = new CloudFoundryWaitForDestroyServiceTask(null)

  @Unroll("result is #expectedStatus if oort service is #serviceStatus")
  def "result depends on Oort service status"() {
    given:
    task.oortService = Stub(OortService) {
      getServiceInstance(credentials, cloudProvider, region, serviceInstanceName) >>
        new ImmutableMap.Builder<String, Object>()
          .put("status", serviceStatus)
          .build()
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), opType, context)

    when:
    def result = task.execute(stage)

    then:
    result.status == expectedStatus

    where:
    credentials = "my-account"
    cloudProvider = "cloud"
    opType = "destroyService"
    region = "org > space"
    serviceInstanceName = "service-instance-name"

    context = [
      "cloudProvider": cloudProvider,
      "service.account": credentials,
      "service.region": region,
      "service.instance.name": serviceInstanceName
    ]

    serviceStatus | expectedStatus
    "FAILED"      | ExecutionStatus.TERMINAL
    "IN_PROGRESS" | ExecutionStatus.RUNNING
  }

  def "returns RUNNING for empty Oort service status"() {
    given:
    task.oortService = Stub(OortService) {
      getServiceInstance(credentials, cloudProvider, region, serviceInstanceName) >>
        new ImmutableMap.Builder<String, Object>().build()
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), opType, context)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    where:
    credentials = "my-account"
    cloudProvider = "cloud"
    opType = "deployService"
    region = "org > space"
    serviceInstanceName = "service-instance-name"

    context = [
      "cloudProvider": cloudProvider,
      "service.account": credentials,
      "service.region": region,
      "service.instance.name": serviceInstanceName
    ]
  }

  def "returns SUCCEEDED for non-existent Oort service status"() {
    given:
    task.oortService = Stub(OortService) {
      getServiceInstance(credentials, cloudProvider, region, serviceInstanceName) >> null
    }

    and:
    def stage = new Stage(Execution.newPipeline("orca"), opType, context)

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    credentials = "my-account"
    cloudProvider = "cloud"
    opType = "destroyService"
    region = "org > space"
    serviceInstanceName = "service-instance-name"

    context = [
      "cloudProvider": cloudProvider,
      "service.account": credentials,
      "service.region": region,
      "service.instance.name": serviceInstanceName
    ]
  }
}
