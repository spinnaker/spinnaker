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

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GoogleSecurityGroupUpserterSpec extends Specification {

  @Subject
  GoogleSecurityGroupUpserter upserter

  def "should return operations and extra outputs"() {
    given:
      upserter = new GoogleSecurityGroupUpserter()
      def ctx = [
          name       : "test-security-group",
          region     : "global",
          credentials: "abc",
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", ctx)

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
        name        : "test-security-group",
        region      : "global",
        credentials : "abc",
        sourceRanges: [],
        ipIngress   : []
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", ctx)
      upserter = new GoogleSecurityGroupUpserter(mortService: mortService)

    when:
      def result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> sg
      result

    when:
      result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> null
      !result

    when:
      result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> {
        throw RetrofitError.httpError("/", new Response("", 404, "", [], null), null, null)
      }
    !result

    when:
      upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> {
        throw RetrofitError.httpError("/", new Response("", 400, "", [], null), null, null)
      }
      thrown(RetrofitError)
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
        name        : "test-security-group",
        region      : "global",
        credentials : "abc",
        sourceRanges: ["192.168.1.100/32"],
        ipIngress   : [
          [
            startPort: 8080,
            endPort: 8083,
            type: "tcp"
          ]
        ]
      ]
      def stage = new Stage<>(new Pipeline(), "whatever", ctx)
      upserter = new GoogleSecurityGroupUpserter(mortService: mortService, objectMapper: OrcaObjectMapper.newInstance())

    when:
      def result = upserter.isSecurityGroupUpserted(sg, stage)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> sg
      result == matches

    where:
      cachedEndPort | cachedSourceRangeIp || matches
      8083          | "192.168.1.100"     || true
      8084          | "192.168.1.100"     || false
      8083          | "192.168.1.101"     || false
      8084          | "192.168.1.101"     || false
  }
}
