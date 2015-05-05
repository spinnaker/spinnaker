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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.tasks.securitygroup.WaitForUpsertedSecurityGroupTask
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static com.netflix.spinnaker.orca.mort.MortService.SecurityGroup.*

class WaitForUpsertedSecurityGroupTaskSpec extends Specification {

  @Subject
  def task = new WaitForUpsertedSecurityGroupTask()

  @Shared
  def error404 = RetrofitError.httpError(null, new Response("", HTTP_NOT_FOUND, "Not Found", [], null), null, null)

  @Unroll
  void "should be RUNNING if current security group does not reflect expected"() {
    given:
    def pipeline = new Pipeline()
    task.mortService = Stub(MortService) {
      getSecurityGroup(_, _, _, _, _) >> {
        currentSecurityGroupProvider.call()
      }
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      targets             : [
        [account: account, region: region, name: groupName]
      ],
      securityGroupIngress: filterForSecurityGroupIngress(task.mortService, expectedSecurityGroup)
    ])

    expect:
    task.execute(stage.asImmutable()).status == taskStatus

    where:
    expectedSecurityGroup                 | currentSecurityGroupProvider              || taskStatus
    null                                  | { bSG(bIR("S1", 7000)) }                  || ExecutionStatus.RUNNING
    bSG(bIR("S2", 7000))                  | { throw error404 }                        || ExecutionStatus.RUNNING
    bSG(bIR("S2", 7000))                  | { bSG(bIR("S1", 7000)) }                  || ExecutionStatus.RUNNING
    bSG(bIR("S1", 7000, 7001))            | { bSG(bIR("S1", 7000)) }                  || ExecutionStatus.RUNNING
    bSG(bIR("S1", 7000), bIR("S2", 7001)) | { bSG(bIR("S1", 7000)) }                  || ExecutionStatus.RUNNING
    bSG(bIR("S1", 7000), bIR("S2", 7001)) | { bSG(bIR("S1", 7000), bIR("S2", 7001)) } || ExecutionStatus.SUCCEEDED
    bSG(bIR("S1", 7000, 7001))            | { bSG(bIR("S1", 7000, 7001)) }            || ExecutionStatus.SUCCEEDED
    bSG(bIR("S1", 7000))                  | { bSG(bIR("S1", 7000)) }                  || ExecutionStatus.SUCCEEDED
    bSG([:])                              | { bSG([:]) }                              || ExecutionStatus.SUCCEEDED

    account = "account"
    region = "us-west-1"
    groupName = "securityGroup"
  }

  private static MortService.SecurityGroup bSG(Map<String, Object>... inboundRules) {
    return new MortService.SecurityGroup(inboundRules: inboundRules)
  }

  private static Map<String, Object> bIR(String securityGroupName, Integer... ports) {
    return [
      securityGroup: [
        name: securityGroupName
      ],
      protocol     : "tcp",
      portRanges   : ports.collect {
        [startPort: it, endPort: it]
      }
    ]
  }

}
