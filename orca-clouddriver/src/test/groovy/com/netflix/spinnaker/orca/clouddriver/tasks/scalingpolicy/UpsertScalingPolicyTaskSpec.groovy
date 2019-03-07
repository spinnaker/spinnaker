/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.scalingpolicy

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import rx.Observable

class UpsertScalingPolicyTaskSpec extends Specification {

  @Shared
  def taskId = new TaskId(UUID.randomUUID().toString())

  @Unroll
  def "should retry task on exception"() {

    given:
    KatoService katoService = Mock(KatoService)
    def task = new UpsertScalingPolicyTask(kato: katoService)
    def stage = new Stage(Execution.newPipeline("orca"), "upsertScalingPolicy",
      [credentials                : "abc", cloudProvider: "aCloud",
       estimatedInstanceWarmup    : "300",
       targetValue                : "75",
       targetTrackingConfiguration:
         [predefinedMetricSpecification:
            [predefinedMetricType: "ASGAverageCPUUtilization"]]])

    when:
    def result = task.execute(stage)

    then:
    1 * katoService.requestOperations(_, _) >> { throw new Exception() }
    result.status.toString() == "RUNNING"

  }

  @Unroll
  def "should set the task status to SUCCEEDED for successful execution"() {

    given:
    KatoService katoService = Mock(KatoService)
    def task = new UpsertScalingPolicyTask(kato: katoService)
    def stage = new Stage(Execution.newPipeline("orca"), "upsertScalingPolicy",
      [credentials                : "abc", cloudProvider: "aCloud",
       estimatedInstanceWarmup    : "300",
       targetValue                : "75",
       targetTrackingConfiguration:
         [predefinedMetricSpecification:
            [predefinedMetricType: "ASGAverageCPUUtilization"]]])

    when:
    def result = task.execute(stage)

    then:
    1 * katoService.requestOperations(_, _) >> { Observable.from(taskId) }
    result.status.toString() == "SUCCEEDED"
  }
}
