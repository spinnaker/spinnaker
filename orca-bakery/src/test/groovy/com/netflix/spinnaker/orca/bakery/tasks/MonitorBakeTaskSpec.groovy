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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.PipelineStatus
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.Stage
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static java.util.UUID.randomUUID

class MonitorBakeTaskSpec extends Specification {

  @Subject task = new MonitorBakeTask()

  @Unroll
  def "should return #taskStatus if bake is #bakeState"() {
    given:
    def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
    def stage = new Stage("bake", [region: "us-west-1", status: previousStatus])

    and:
    task.bakery = Stub(BakeryService) {
      lookupStatus(stage.context.region, id) >> Observable.from(new BakeStatus(id: id, state: bakeState))
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    bakeState                  | taskStatus
    BakeStatus.State.PENDING   | PipelineStatus.RUNNING
    BakeStatus.State.RUNNING   | PipelineStatus.RUNNING
    BakeStatus.State.COMPLETED | PipelineStatus.SUCCEEDED
    BakeStatus.State.CANCELLED | PipelineStatus.FAILED
    BakeStatus.State.SUSPENDED | PipelineStatus.RUNNING

    id = randomUUID().toString()
  }

  def "outputs the updated bake status"() {
    given:
    def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
    def stage = new Stage("bake", [region: "us-west-1", status: previousStatus])

    and:
    task.bakery = Stub(BakeryService) {
      lookupStatus(stage.context.region, id) >> Observable.from(new BakeStatus(id: id, state: BakeStatus.State.COMPLETED))
    }

    when:
    def result = task.execute(stage)

    then:
    with(result.outputs.status) {
      id == previousStatus.id
      state == BakeStatus.State.COMPLETED
    }

    where:
    id = randomUUID().toString()
  }

}
