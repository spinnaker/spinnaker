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
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForUpsertedSecurityGroupTaskSpec extends Specification {

  @Subject task = new WaitForUpsertedSecurityGroupTask()

  @Unroll
  void "should return #taskStatus status when group was #old and is now '#current' per Mort"() {
    given:
    def pipeline = new Pipeline()
    def groupName = 'group'
    def account = 'account'
    def region = 'region'
    task.mortService = Stub(MortService) {
      getSecurityGroup(account, 'aws', groupName, region) >> new Response('mort', 200, 'ok', [], new TypedString(current))
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "upsert.account": account,
      "upsert.region" : region,
      "upsert.name"   : groupName
    ])
    if (old) {
      stage.context."pre.response" = old
    }

    expect:
    task.execute(stage.asImmutable()).status == taskStatus

    where:
    old | current || taskStatus
    null       | ''         || ExecutionStatus.RUNNING
    null       | 'changed'  || ExecutionStatus.SUCCEEDED
    'original' | 'original' || ExecutionStatus.RUNNING
    'original' | 'changed'  || ExecutionStatus.SUCCEEDED
  }

}
