/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class DetermineSourceServerGroupTaskSpec extends Specification {

  void 'should include source in context'() {
    given:
    Stage stage = new PipelineStage(new Pipeline(), 'deploy', 'deploy', [
      account          : account,
      application      : 'foo',
      availabilityZones: [(region): []]])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> new StageData.Source(account: account, region: region, asgName: asgName)

    and:
    result.stageOutputs.source.account == account
    result.stageOutputs.source.region == region
    result.stageOutputs.source.asgName == asgName
    result.stageOutputs.source.useSourceCapacity == null

    where:
    account = 'test'
    region = 'us-east-1'
    asgName = 'foo-test-v000'
  }

  void 'should useSourceCapacity from context if not provided in Source'() {
    given:
    Stage stage = new PipelineStage(new Pipeline(), 'deploy', 'deploy', [useSourceCapacity: contextUseSourceCapacity])

    def resolver = Mock(SourceResolver)

    when:
    def result = new DetermineSourceServerGroupTask(sourceResolver: resolver).execute(stage)

    then:
    1 * resolver.getSource(_) >> new StageData.Source(account: 'test', region: 'us-east-1', asgName: 'foo-v001', useSourceCapacity: sourceUseSourceCapacity)

    and:
    result.stageOutputs.source.useSourceCapacity == expectedUseSourceCapacity

    where:
    contextUseSourceCapacity | sourceUseSourceCapacity | expectedUseSourceCapacity
    null                     | null                    | null
    null                     | true                    | true
    false                    | true                    | true
    false                    | null                    | false
    true                     | null                    | true
  }
}
