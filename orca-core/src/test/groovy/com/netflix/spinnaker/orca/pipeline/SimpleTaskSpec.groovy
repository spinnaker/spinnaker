/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.api.SimpleStage
import com.netflix.spinnaker.orca.api.SimpleStageInput
import com.netflix.spinnaker.orca.api.SimpleStageOutput
import com.netflix.spinnaker.orca.api.SimpleStageStatus
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class SimpleTaskSpec extends Specification {
  private static class MyStage implements SimpleStage<Object> {
    @Override
    String getName() {
      return "myStage"
    }

    @Override
    SimpleStageOutput execute(SimpleStageInput<Object> input) {
      SimpleStageOutput output = new SimpleStageOutput()

      Map<String, String> stageOutput = new HashMap<>()
      stageOutput.put("hello", "world")

      output.setStatus(SimpleStageStatus.SUCCEEDED)
      output.setOutput(stageOutput)
      return output
    }
  }

  @Subject
  def myStage = new MyStage()

  @Unroll
  def "should check dynamic config property"() {
    when:
    def task = new SimpleTask(myStage)
    def results = task.execute(new com.netflix.spinnaker.orca.pipeline.model.Stage())

    then:
    results.getStatus() == ExecutionStatus.SUCCEEDED
    results.outputs.hello == "world"
  }
}
