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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertAsgScheduledActionsTaskSpec extends Specification {
  @Subject
  def task = new UpsertAsgScheduledActionsTask()

  void "should group server groups by region"() {
    given:
    def pipeline = new Pipeline()
    task.kato = Mock(KatoService) {
      1 * requestOperations(*_) >> {
        rx.Observable.from(new TaskId(UUID.randomUUID().toString()))
      }
    }
    def context = [ asgs: [
        [ asgName: "asg-v001", region: "us-east-1"],
        [ asgName: "asg-v002", region: "us-east-1"],
        [ asgName: "asg-v003", region: "us-west-1"],
    ]]

    and:
    def stage = new PipelineStage(pipeline, "upsertAsgScheduledActions", context).asImmutable()

    when:
    def executionContext = task.execute(stage)

    then:
    executionContext.stageOutputs."deploy.server.groups".toString() == ['us-east-1': ['asg-v001', 'asg-v002'], 'us-west-1': ['asg-v003']].toString()
  }
}
