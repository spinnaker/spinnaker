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


package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.commands.InstanceUptimeCommand
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification

class CaptureInstanceUptimeTaskSpec extends Specification {
  def "should noop if `instanceUptimeCommand` is not available"() {
    given:
    def task = new CaptureInstanceUptimeTask(instanceUptimeCommand: null)
    def stage = new StageExecutionImpl()

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    (result.context as Map) == [instanceUptimes: [:]]
  }

  def "should capture instance uptime for every instanceId"() {
    given:
    def task = new CaptureInstanceUptimeTask()
    CloudDriverService cloudDriverService = Mock()
    task.cloudDriverService = cloudDriverService
    cloudDriverService.getInstance(_,_,_) >> { a, r, instanceId -> [instanceId: instanceId] }
    task.instanceUptimeCommand = Mock(InstanceUptimeCommand)
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [instanceIds: ["1", "2", "3"]])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    (result.context as Map) == [instanceUptimes: ["1": 1, "2": 2]]

    1 * task.instanceUptimeCommand.uptime("aws", [instanceId: "1"]) >> {
      return new InstanceUptimeCommand.InstanceUptimeResult(1)
    }
    1 * task.instanceUptimeCommand.uptime("aws", [instanceId: "2"]) >> {
      return new InstanceUptimeCommand.InstanceUptimeResult(2)
    }
    1 * task.instanceUptimeCommand.uptime("aws", [instanceId: "3"]) >> {
      throw new RuntimeException("Should be skipped!")
    }
  }
}
