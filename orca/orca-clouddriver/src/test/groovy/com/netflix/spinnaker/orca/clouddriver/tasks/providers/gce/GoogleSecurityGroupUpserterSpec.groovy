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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class GoogleSecurityGroupUpserterSpec extends Specification {

  @Subject
  GoogleSecurityGroupUpserter upserter

  def "should return operations and extra outputs"() {
    given:
      upserter = new GoogleSecurityGroupUpserter()
      def ctx = [
          securityGroupName : "test-security-group",
          region            : "global",
          credentials       : "abc",
      ]
    def pipe = pipeline {
      application = "orca"
    }
    def stage = new StageExecutionImpl(pipe, "whatever", ctx)

    when:
      def results = upserter.getOperationContext(stage)

    then:
      results

      def ops = results.operations
      ops.size() == 1
      (ops[0] as Map).upsertSecurityGroup == ctx

      def extraOutputs = results.extraOutput
      List<MortService.SecurityGroup> targets = extraOutputs.targets
      targets.size() == 1
      targets[0].name == "test-security-group"
      targets[0].region == "global"
      targets[0].accountName == "abc"
  }

  def "should return the correct result if the security group has been upserted"() {
    given:
      MortService.SecurityGroup sg = new MortService.SecurityGroup(name: "test-security-group",
                                                                   region: "global",
                                                                   accountName: "abc",
                                                                   inboundRules: [])
      MortService mortService = Mock(MortService)

      def ctx = [
        securityGroupName : "test-security-group",
        region            : "global",
        credentials       : "abc",
        sourceRanges      : [],
        ipIngress         : []
      ]
    def pipe = pipeline {
      application = "orca"
    }
    def stage = new StageExecutionImpl(pipe, "whatever", ctx)
      upserter = new GoogleSecurityGroupUpserter(mortService: mortService)

    when:
      def result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> Calls.response(sg)
      result

    when:
      result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> Calls.response(null)
      !result

    when:
      result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> {
        throw makeSpinnakerHttpException(404)
      }
    !result

    when:
      upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> {
        throw makeSpinnakerHttpException(400)
      }
      thrown(SpinnakerHttpException)
  }

  @Unroll
  def "should return the correct result if the source ranges and ip ingress rules match"() {
    given:
      MortService.SecurityGroup sg = new MortService.SecurityGroup(name: "test-security-group",
        region: "global",
        accountName: "abc",
        inboundRules: [
          [
            portRanges: [[
              startPort: 8080,
              endPort: cachedEndPort
            ]],
            protocol: "tcp",
            range: [
              cidr: "/32",
              ip: cachedSourceRangeIp
            ]
          ]
        ])
      MortService mortService = Mock(MortService)

      def ctx = [
        securityGroupName : "test-security-group",
        region            : "global",
        credentials       : "abc",
        sourceRanges      : ["192.168.1.100/32"],
        ipIngress         : [
          [
            startPort: 8080,
            endPort: 8083,
            type: "tcp"
          ]
        ]
      ]
    def pipe = pipeline {
      application = "orca"
    }
    def stage = new StageExecutionImpl(pipe, "whatever", ctx)
      upserter = new GoogleSecurityGroupUpserter(mortService: mortService, objectMapper: OrcaObjectMapper.newInstance())

    when:
      def result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> Calls.response(sg)
      result == matches

    where:
      cachedEndPort | cachedSourceRangeIp || matches
      8083          | "192.168.1.100"     || true
      8084          | "192.168.1.100"     || false
      8083          | "192.168.1.101"     || false
      8084          | "192.168.1.101"     || false
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status, String message = "{ \"message\": \"arbitrary message\" }") {
    String url = "https://mort";
    Response retrofit2Response =
        Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), message))

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()

    return new SpinnakerHttpException(retrofit2Response, retrofit)
  }
}
