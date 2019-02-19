/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.openstack

import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class OpenstackSecurityGroupUpserterSpec extends Specification {

  @Subject
  OpenstackSecurityGroupUpserter upserter

  def "should return operations and extra outputs"() {
    given:
    upserter = new OpenstackSecurityGroupUpserter()
    def context = [
      securityGroupName : 'my-security-group',
      region            : 'west',
      credentials       : 'cred'
    ]
    def pipe = pipeline {
      application = "orca"
    }
    def stage = new Stage(pipe, 'whatever', context)

    when:
    def results = upserter.getOperationContext(stage)

    then:
    results

    def ops = results.operations
    ops.size() == 1
    (ops[0] as Map).upsertSecurityGroup == context

    def extraOutputs = results.extraOutput
    List<MortService.SecurityGroup> targets = extraOutputs.targets
    targets.size() == 1
    targets[0].name == 'my-security-group'
    targets[0].region == 'west'
    targets[0].accountName == 'cred'
  }

  def "should return the correct result if the security group has been upserted"() {
    given:
    MortService.SecurityGroup sg = new MortService.SecurityGroup(
      name: "my-security-group",
      region: "west",
      accountName: "abc")
    MortService mortService = Mock(MortService) {
      1 * getSecurityGroup("abc", "openstack", "my-security-group", "west") >> sg
    }
    upserter = new OpenstackSecurityGroupUpserter(mortService: mortService)

    when:
    def result = upserter.isSecurityGroupUpserted(sg, null)

    then:
    result
  }

  def "handles null when getting the security group"() {
    given:
    MortService.SecurityGroup sg = new MortService.SecurityGroup(
      name: "my-security-group",
      region: "west",
      accountName: "abc")
    MortService mortService = Mock(MortService) {
      1 * getSecurityGroup("abc", "openstack", "my-security-group", "west") >> null
    }
    upserter = new OpenstackSecurityGroupUpserter(mortService: mortService)

    when:
    def result = upserter.isSecurityGroupUpserted(sg, null)

    then:
    !result
  }

  def "returns false for 404 retrofit error"() {
    given:
    MortService.SecurityGroup sg = new MortService.SecurityGroup(
      name: "my-security-group",
      region: "west",
      accountName: "abc")
    MortService mortService = Mock(MortService) {
      1 * getSecurityGroup("abc", "openstack", "my-security-group", "west") >> {
        throw RetrofitError.httpError("/", new Response("", 404, "", [], null), null, null)
      }
    }
    upserter = new OpenstackSecurityGroupUpserter(mortService: mortService)

    when:
    def result = upserter.isSecurityGroupUpserted(sg, null)

    then:
    !result
  }

  def "throws error for non-404 retrofit error"() {
    given:
    MortService.SecurityGroup sg = new MortService.SecurityGroup(
      name: "my-security-group",
      region: "west",
      accountName: "abc")
    MortService mortService = Mock(MortService) {
      1 * getSecurityGroup("abc", "openstack", "my-security-group", "west") >> {
        throw RetrofitError.httpError("/", new Response("", 400, "", [], null), null, null)
      }
    }
    upserter = new OpenstackSecurityGroupUpserter(mortService: mortService)

    when:
    def result = upserter.isSecurityGroupUpserted(sg, null)

    then:
    thrown(RetrofitError)
  }

}
