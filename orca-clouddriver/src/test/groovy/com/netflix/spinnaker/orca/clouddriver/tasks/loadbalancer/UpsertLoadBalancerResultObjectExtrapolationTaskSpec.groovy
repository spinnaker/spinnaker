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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertLoadBalancerResultObjectExtrapolationTaskSpec extends Specification {

  @Subject
    task = new UpsertLoadBalancerResultObjectExtrapolationTask()

  def dnsName = "my-awesome-elb.amazonaws.com"

  def katoTasks = [
    [
      id           : 1,
      resultObjects: [
        [
          loadBalancers: [
            "us-east-1": [
              dnsName: dnsName
            ]
          ]
        ]
      ]
    ]
  ]

  void "should put extrapolate resulting DNS name from resultObjects"() {
    setup:
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "whatever", ["kato.tasks": katoTasks, "kato.last.task.id": new TaskId("1")])

    when:
    def result = task.execute(stage)

    then:
    result.outputs.dnsName == dnsName
  }

}
