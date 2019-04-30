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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static java.util.UUID.randomUUID

class MonitorBakeTaskSpec extends Specification {

  @Subject
  def task = new MonitorBakeTask()

  @Shared
  def pipeline = pipeline()

  @Unroll
  def "should return #taskStatus if bake is #bakeState"() {
    given:
    def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
    def stage = new Stage(pipeline, "bake", [region: "us-west-1", status: previousStatus])

    and:
    task.bakery = Stub(BakeryService) {
      lookupStatus(stage.context.region, id) >> Observable.from(new BakeStatus(id: id, state: bakeState, result: bakeResult))
    }

    expect:
    task.execute(stage).status == taskStatus

    where:
    bakeState                  | bakeResult                || taskStatus
    BakeStatus.State.PENDING   | null                      || ExecutionStatus.RUNNING
    BakeStatus.State.RUNNING   | null                      || ExecutionStatus.RUNNING
    BakeStatus.State.COMPLETED | BakeStatus.Result.SUCCESS || ExecutionStatus.SUCCEEDED
    BakeStatus.State.COMPLETED | BakeStatus.Result.FAILURE || ExecutionStatus.TERMINAL
    BakeStatus.State.COMPLETED | null                      || ExecutionStatus.TERMINAL
    BakeStatus.State.SUSPENDED | null                      || ExecutionStatus.RUNNING

    id = randomUUID().toString()
  }

  @Unroll
  def "should attempt a new bake when previous status is PENDING and current status is CANCELED or CANCELLED"() {
    given:
    def id = randomUUID().toString()
    def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
    def stage = new Stage(pipeline, "bake", [region: "us-west-1", status: previousStatus])

    and:
    task.bakery = Stub(BakeryService) {
      lookupStatus(stage.context.region, id) >> Observable.from(
        new BakeStatus(id: id, state: state, result: null)
      )
    }
    task.createBakeTask = Mock(CreateBakeTask) {
      1 * execute(_) >> { return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([stage: 1]).outputs([global: 2]).build() }
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    (result.context as Map) == [stage: 1]
    (result.outputs as Map) == [global: 2]

    where:
    state << [BakeStatus.State.CANCELED, BakeStatus.State.CANCELLED]
  }

  def "outputs the updated bake status"() {
    given:
    def previousStatus = new BakeStatus(id: id, state: BakeStatus.State.PENDING)
    def stage = new Stage(pipeline, "bake", [region: "us-west-1", status: previousStatus])

    and:
    task.bakery = Stub(BakeryService) {
      lookupStatus(stage.context.region, id) >> Observable.from(new BakeStatus(id: id, state: BakeStatus.State.COMPLETED))
    }

    when:
    def result = task.execute(stage)

    then:
    with(result.context.status) {
      id == previousStatus.id
      state == BakeStatus.State.COMPLETED
    }

    where:
    id = randomUUID().toString()
  }
}
