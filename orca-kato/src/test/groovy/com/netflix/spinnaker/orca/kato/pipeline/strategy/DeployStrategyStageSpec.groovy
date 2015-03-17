/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Unroll

class DeployStrategyStageSpec extends Specification {
  @Unroll
  void "should include freeFormDetails when building cluster name"() {
    given:
    def stage = new PipelineStage(
      new Pipeline(),
      "deploy",
      [
        application    : application,
        stack          : stack,
        freeFormDetails: freeFormDetails
      ]
    )

    expect:
    stage.mapTo(DeployStrategyStage.StageData).getCluster() == cluster

    where:
    application | stack        | freeFormDetails || cluster
    "myapp"     | "prestaging" | "freeform"      || "myapp-prestaging-freeform"
    "myapp"     | "prestaging" | null            || "myapp-prestaging"
    "myapp"     | null         | "freeform"      || "myapp--freeform"
    "myapp"     | null         | null            || "myapp"
  }

  @Unroll
  void "stage data should favor account over credentials"() {
    given:
    def stage = new PipelineStage(
      new Pipeline(),
      "deploy",
      [
        account    : account,
        credentials: credentials
      ]
    )

    when:
    def mappedAccount
    try {
      mappedAccount = stage.mapTo(DeployStrategyStage.StageData).getAccount()
    } catch (Exception e) {
      mappedAccount = e.class.simpleName
    }

    then:
    mappedAccount == expectedAccount

    where:
    account | credentials || expectedAccount
    "test"  | "prod"      || "IllegalStateException"
    "test"  | null        || "test"
    null    | "test"      || "test"
  }

  void "should handle only when there are existing clusters"() {
    given:
    Stage stage = new PipelineStage(new Pipeline(), 'deploy', 'deploy', [
      account          : 'test',
      application      : 'foo',
      stack            : 'test',
      strategy         : 'redblack', //Strategy.RED_BLACK.key, -- y u no work
      availabilityZones: ['us-east-1': [], 'us-west-2': []]])

    TypedByteArray oortClusters = new TypedByteArray('application/json', new ObjectMapper().writeValueAsBytes([
      serverGroups: [
        [region: 'us-east-1', name: 'foo-test-v000'],
        [region: 'us-east-1', name: 'foo-test-v001']
      ]
    ]))

    def oort = Mock(OortService)


    when:
    new TestDeployStrategyStage(oort: oort, mapper: new ObjectMapper()).composeRedBlackFlow(stage)

    then:
    1 * oort.getCluster('foo', 'test', 'foo-test', 'aws') >> new Response('http://oortse.cx', 200, 'OK', [], oortClusters)

    and:
    stage.afterStages.size() == 1
    with(stage.afterStages.first()) {
      name == 'disable'
      context.asgName == 'foo-test-v001'
    }
  }

  static class TestDeployStrategyStage extends DeployStrategyStage {
    TestDeployStrategyStage() {
      super('test')
    }

    @Override
    protected List<Step> basicSteps(Stage stage) {
      []
    }
  }
}
