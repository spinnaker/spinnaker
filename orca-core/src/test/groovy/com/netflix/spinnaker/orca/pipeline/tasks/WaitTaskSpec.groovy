/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks

import java.time.Duration
import com.netflix.spinnaker.orca.time.MutableClock
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WaitTaskSpec extends Specification {
  def clock = new MutableClock()
  @Subject
    task = new WaitTask(clock)

  void "should wait for a configured period"() {
    setup:
    def wait = 5
    def stage = stage {
      refId = "1"
      type = "wait"
      context["waitTime"] = wait
      startTime = clock.instant().toEpochMilli()
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == RUNNING

    when:
    clock.incrementBy(Duration.ofSeconds(10))

    and:
    result = task.execute(stage)

    then:
    result.status == SUCCEEDED
  }

  void "should return backoff based on waitTime"() {
    def wait = 300
    def stage = stage {
      refId = "1"
      type = "wait"
      context["waitTime"] = wait
      startTime = clock.instant().toEpochMilli()
    }

    when:
    def result = task.execute(stage)

    and:
    def backOff = task.getDynamicBackoffPeriod(stage, null)

    then:
    result.status == RUNNING
    backOff == TimeUnit.SECONDS.toMillis(wait)
  }

  void "should skip waiting when marked in context"() {
    setup:
    def stage = stage {
      refId = "1"
      type = "wait"
      context["waitTime"] = 1_000_000
      startTime = clock.instant().toEpochMilli()
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == RUNNING

    when:
    clock.incrementBy(Duration.ofSeconds(10))
    stage.context.skipRemainingWait = true

    and:
    result = task.execute(stage)

    then:
    result.status == SUCCEEDED

  }
}
