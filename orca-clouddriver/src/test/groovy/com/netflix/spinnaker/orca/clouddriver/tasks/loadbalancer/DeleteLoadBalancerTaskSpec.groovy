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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

/**
 * Created by aglover on 9/26/14.
 */
class DeleteLoadBalancerTaskSpec extends Specification {
  @Subject task = new DeleteLoadBalancerTask()
  def stage = new PipelineStage(type: "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def deleteLoadBalancerConfig = [
    cloudProvider   : 'gce',
    loadBalancerName: "flapjack-frontend",
    regions         : ["us-central1"],
    credentials     : "fzlem"
  ]

  def setup() {
    stage.context.putAll(deleteLoadBalancerConfig)
  }

  def "creates a delete load balancer task based on job parameters"() {
    given:
    def operations
    task.kato = Mock(KatoService) {
      1 * requestOperations(this.deleteLoadBalancerConfig.cloudProvider, _) >> {
        operations = it[1]
        rx.Observable.from(taskId)
      }
    }

    when:
    task.execute(stage)

    then:
    operations.size() == 1
    with(operations[0].deleteLoadBalancer) {
      it instanceof Map
      loadBalancerName == this.deleteLoadBalancerConfig.loadBalancerName
      regions == this.deleteLoadBalancerConfig.regions
      credentials == this.deleteLoadBalancerConfig.credentials
    }
  }

  def "returns a success status with the kato task id"() {
    given:
    task.kato = Stub(KatoService) {
      requestOperations(this.deleteLoadBalancerConfig.cloudProvider, _) >> rx.Observable.from(taskId)
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    result.outputs."kato.last.task.id" == taskId
    result.outputs."delete.account.name" == deleteLoadBalancerConfig.credentials
  }
}
