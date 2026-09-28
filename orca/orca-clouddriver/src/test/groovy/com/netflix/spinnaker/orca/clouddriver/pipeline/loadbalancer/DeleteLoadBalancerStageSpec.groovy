/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.loadbalancer

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeleteLoadBalancerStageSpec extends Specification {

  @Unroll
  def "orders the cache refresh for #cloudProvider #loadBalancerType"() {
    given:
    def deleteStage = stage {
      context = [cloudProvider: cloudProvider, loadBalancerType: loadBalancerType]
    }

    when:
    def graph = TaskNode.build(TaskNode.GraphType.FULL) {
      new DeleteLoadBalancerStage().taskGraph(deleteStage, it)
    }

    then:
    graph*.name == expectedTasks

    where:
    cloudProvider | loadBalancerType            || expectedTasks
    "gce"         | "EXTERNAL_MANAGED"          || ["deleteLoadBalancer", "monitorDelete", "forceCacheRefresh"]
    "gce"         | "REGIONAL_EXTERNAL_NETWORK" || ["deleteLoadBalancer", "monitorDelete", "forceCacheRefresh"]
    "gce"         | "INTERNAL_MANAGED"          || ["deleteLoadBalancer", "forceCacheRefresh", "monitorDelete"]
    "aws"         | "application"               || ["deleteLoadBalancer", "forceCacheRefresh", "monitorDelete"]
    "azure"       | null                        || ["deleteLoadBalancer", "forceCacheRefresh", "monitorDelete"]
  }
}
