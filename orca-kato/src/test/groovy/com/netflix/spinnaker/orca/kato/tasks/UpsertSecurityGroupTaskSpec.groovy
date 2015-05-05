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

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.kato.tasks.securitygroup.UpsertSecurityGroupTask
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertSecurityGroupTaskSpec extends Specification {
  @Subject
  def task = new UpsertSecurityGroupTask()

  @Shared
  def defaultContext = [name: "SG1", credentials: "test"]

  @Unroll
  void "should build multiple operations corresponding to each target region"() {
    given:
    def pipeline = new Pipeline()
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        operations = it[0] as Collection
        assert operations.size() == expectedTargets.size()
        rx.Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }
    task.mapper = new OrcaObjectMapper()
    task.mortService = Mock(MortService) {
      1 * getVPCs() >> allVPCs
    }

    and:
    def stage = new PipelineStage(pipeline, "upsertSecurityGroup", context).asImmutable()

    when:
    def executionContext = task.execute(stage)

    then:
    (executionContext.stageOutputs.targets as List<Map>) == expectedTargets as List<Map>

    where:
    context                                                                 || expectedTargets
    defaultContext + [region: "us-west-1"]                                  || [bT("test", "us-west-1", null, "SG1")]
    defaultContext + [regions: ["us-west-1", "us-east-1"]]                  || [bT("test", "us-west-1", null, "SG1"), bT("test", "us-east-1", null, "SG1")]
    defaultContext + [regions: ["us-west-1", "us-east-1"], vpcId: "vpc1-0"] || [bT("test", "us-west-1", "vpc1-0", "SG1"), bT("test", "us-east-1", "vpc1-1", "SG1")]
    defaultContext + [regions: ["us-west-1"], vpcId: "vpc1-0"]              || [bT("test", "us-west-1", "vpc1-0", "SG1")]

    allVPCs = [
      new MortService.VPC(id: "vpc1-0", name: "vpc1", region: "us-west-1", account: "test"),
      new MortService.VPC(id: "vpc1-1", name: "vpc1", region: "us-east-1", account: "test"),
    ]
  }

  void "should throw exception if no target regions specified"() {
    given:
    task.mapper = new OrcaObjectMapper()
    def stage = new PipelineStage(new Pipeline(), "upsertSecurityGroup", [:]).asImmutable()

    when:
    task.execute(stage)

    then:
    thrown(IllegalStateException)
  }

  MortService.SecurityGroup bSG(String name) {
    new MortService.SecurityGroup(name: name)
  }

  private static Map bT(String credentials, String region, String vpcId, String name) {
    return [
      credentials: credentials,
      region     : region,
      vpcId      : vpcId,
      name       : name
    ]
  }
}
