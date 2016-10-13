/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.clouddriver.MortService.SecurityGroup.filterForSecurityGroupIngress
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class AmazonSecurityGroupUpserterSpec extends Specification {

  @Subject
  def upserter = new AmazonSecurityGroupUpserter()

  @Shared
  def ctx = [name: "SG1", credentials: "test"]

  @Shared
  def error404 = RetrofitError.httpError(null, new Response("", HTTP_NOT_FOUND, "Not Found", [], null), null, null)

  def "should throw exception on missing region"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "upsertSecurityGroup", [:])

    when:
      upserter.getOperationContext(stage)

    then:
      thrown(IllegalStateException)
  }

  @Unroll
  def "should return ops and extra outputs"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "upsertSecurityGroup", context)
      upserter.mortService = Mock(MortService) {
        1 * getVPCs() >> allVPCs
      }

    when:
      def results = upserter.getOperationContext(stage)

    then:
      results

      def ops = results.operations
      ops.size() == expectedTargets.size()

      def extraOutputs = results.extraOutput
      extraOutputs.targets == expectedTargets

    where:
      context                                                      || expectedTargets
      ctx + [region: "us-west-1"]                                  || [bT("test", "us-west-1", null, "SG1")]
      ctx + [regions: ["us-west-1", "us-east-1"]]                  || [bT("test", "us-west-1", null, "SG1"), bT("test", "us-east-1", null, "SG1")]
      ctx + [regions: ["us-west-1", "us-east-1"], vpcId: "vpc1-0"] || [bT("test", "us-west-1", "vpc1-0", "SG1"), bT("test", "us-east-1", "vpc1-1", "SG1")]
      ctx + [regions: ["us-west-1"], vpcId: "vpc1-0"]              || [bT("test", "us-west-1", "vpc1-0", "SG1")]

      allVPCs = [
          new MortService.VPC(id: "vpc1-0", name: "vpc1", region: "us-west-1", account: "test"),
          new MortService.VPC(id: "vpc1-1", name: "vpc1", region: "us-east-1", account: "test"),
      ]
  }

  private static MortService.SecurityGroup bT(String credentials, String region, String vpcId, String name) {
    return new MortService.SecurityGroup(name: name,
                                         region: region,
                                         accountName: credentials,
                                         vpcId: vpcId)
  }

  @Unroll
  def "should return the correct result if the security group has been upserted"() {
    given:
      upserter.mortService = Stub(MortService) {
        getSecurityGroup(_, _, _, _, _) >> {
          currentSecurityGroupProvider.call()
        }
      }
      def stage = new PipelineStage(new Pipeline(), "whatever", [
          targets : [bT(account, region, null, groupName)],
          securityGroupIngress: filterForSecurityGroupIngress(upserter.mortService, expectedSecurityGroup)
      ])

    expect:
      isUpdated == upserter.isSecurityGroupUpserted(expectedSecurityGroup, stage)

    where:
      expectedSecurityGroup                 | currentSecurityGroupProvider              || isUpdated
      null                                  | { bSG(bIR("S1", 7000)) }                  || false
      bSG(bIR("S2", 7000))                  | { throw error404 }                        || false
      bSG(bIR("S2", 7000))                  | { bSG(bIR("S1", 7000)) }                  || false
      bSG(bIR("S1", 7000, 7001))            | { bSG(bIR("S1", 7000)) }                  || false
      bSG(bIR("S1", 7000), bIR("S2", 7001)) | { bSG(bIR("S1", 7000)) }                  || false
      bSG(bIR("S1", 7000), bIR("S2", 7001)) | { bSG(bIR("S1", 7000), bIR("S2", 7001)) } || true
      bSG(bIR("S1", 7000, 7001))            | { bSG(bIR("S1", 7000, 7001)) }            || true
      bSG(bIR("S1", 7000))                  | { bSG(bIR("S1", 7000)) }                  || true
      bSG([:])                              | { bSG([:]) }                              || true

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
