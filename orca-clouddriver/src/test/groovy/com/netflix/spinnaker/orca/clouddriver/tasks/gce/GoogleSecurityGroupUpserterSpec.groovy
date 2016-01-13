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

package com.netflix.spinnaker.orca.clouddriver.tasks.gce

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

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
      def stage = new PipelineStage(new Pipeline(), "whatever", ctx)

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
                                                                   accountName: "abc")
      MortService mortService = Mock(MortService) {
        1 * getSecurityGroup("abc", "gce", "test-security-group", "global") >> sg
      }

      upserter = new GoogleSecurityGroupUpserter(mortService: mortService)

    when:
      def result = upserter.isSecurityGroupUpserted(sg, null)

    then:
      result

    when:
      result = upserter.isSecurityGroupUpserted(sg, null)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> null
      !result

    when:
      result = upserter.isSecurityGroupUpserted(sg, null)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> {
        throw RetrofitError.httpError("/", new Response("", 404, "", [], null), null, null)
      }
    !result

    when:
      result = upserter.isSecurityGroupUpserted(sg, null)

    then:
      1 * mortService.getSecurityGroup("abc", "gce", "test-security-group", "global") >> {
        throw RetrofitError.httpError("/", new Response("", 400, "", [], null), null, null)
      }
      thrown(RetrofitError)



  }
}
