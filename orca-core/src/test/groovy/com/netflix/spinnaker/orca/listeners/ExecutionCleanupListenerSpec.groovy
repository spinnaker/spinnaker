/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.listeners

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL

class ExecutionCleanupListenerSpec extends Specification {
  @Subject
  def executionCleanupListener = new ExecutionCleanupListener()

  def persister = Mock(Persister)

  @Unroll
  def "should only cleanup successfully completed pipelines"() {
    given:
    def pipeline = new Pipeline("orca")
    pipeline.status = executionStatus
    pipeline.stages << new Stage<>(pipeline, "", [
      targetReferences: "my-target-reference"
    ])
    pipeline.stages << new Stage<>(pipeline, "", [
      targetReferences: "my-other-target-reference"
    ])

    when:
    executionCleanupListener.afterExecution(persister, pipeline, pipeline.status, true)

    then:
    pipeline.stages.count { it.context.containsKey("targetReferences") } == targetReferencesCount

    persistCountPerStage * persister.save(pipeline.stages[0])
    persistCountPerStage * persister.save(pipeline.stages[1])

    where:
    executionStatus || targetReferencesCount || persistCountPerStage
    SUCCEEDED       || 0                     || 1         // should remove targetReferences and persist each stage
    TERMINAL        || 2                     || 0

  }
}
