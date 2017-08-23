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

package com.netflix.spinnaker.orca.kato.tasks.rollingpush

import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class DetermineTerminationPhaseInstancesTaskSpec extends Specification {

  def task = new DetermineTerminationPhaseInstancesTask()

  def 'should get next instanceIds'() {
    given:
    def context = [termination: [concurrentRelaunches: concurrentRelaunches], terminationInstanceIds: terminationInstanceIds]
    def stage = new Stage<>(new Orchestration(), 'test', context)

    when:
    def result = task.execute(stage)

    then:
    result.context.instanceIds == expectedInstanceIds
    result.context.terminationInstanceIds == expectedTerminationIds

    where:
    concurrentRelaunches | terminationInstanceIds | expectedInstanceIds | expectedTerminationIds
    1                    | ['i-1', 'i-2']         | ['i-1']             | ['i-2']
    2                    | ['i-1', 'i-2']         | ['i-1', 'i-2']      | []
    2                    | ['i-1']                | ['i-1']             | []
    null                 | ['i-1', 'i-2']         | ['i-1']             | ['i-2']
  }
}
