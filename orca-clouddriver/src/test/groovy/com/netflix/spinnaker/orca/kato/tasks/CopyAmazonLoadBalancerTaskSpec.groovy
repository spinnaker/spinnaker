/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class CopyAmazonLoadBalancerTaskSpec extends Specification {
  @Subject
  def task = new CopyAmazonLoadBalancerTask(mortService: Mock(MortService))

  void "should throw exception if load balancer does not exist"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getLoadBalancerDetails("aws", "test", "us-west-1", "example-frontend") >> { return [] }
      0 * _
    }

    when:
    task.execute(new Stage<>(new Pipeline("orca"), "", [
      credentials: "test", region: "us-west-1", name: "example-frontend"
    ]))

    then:
    final IllegalStateException exception = thrown()
    exception.message.startsWith("Load balancer does not exist")
  }

  void "should throw exception if any target specifies > 1 regions"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getLoadBalancerDetails("aws", "test", "us-west-1", "example-frontend") >> { return [["a": "b"]] }
      0 * _
    }

    when:
    task.execute(new Stage<>(new Pipeline("orca"), "", [
      credentials: "test", region: "us-west-1", name: "example-frontend",
      targets    : [
        [
          availabilityZones: ["us-west-1": ["us-west-1a"], "us-west-2": ["us-west-2a"]]
        ]
      ]
    ]))

    then:
    final IllegalStateException exception = thrown()
    exception.message.startsWith("Must specify one (and only one)")
  }

  void "should copy load balancer to multiple targets"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getLoadBalancerDetails("aws", "test", "us-west-1", "example-frontend") >> {
        return [currentLoadBalancer]
      }
    }
    task.katoService = Mock(KatoService) {
      1 * requestOperations(_) >> { List params ->
        assert (params[0] as List).size() == targets.size()
        rx.Observable.from(taskId)
      }
    }

    when:
    def result = task.execute(new Stage<>(new Pipeline("orca"), "", [
      credentials: "test", region: "us-west-1", name: currentLoadBalancer.loadBalancerName, targets: targets
    ]))

    then:
    result.context."notification.type" == "upsertamazonloadbalancer"
    result.context."kato.last.task.id" == taskId
    (result.context.targets as List<Map>) == targets.collect {
      return [
        credentials      : it.credentials,
        availabilityZones: it.availabilityZones,
        vpcId            : it.vpcId,
        name             : it.name ?: currentLoadBalancer.loadBalancerName
      ]
    } as List<Map>

    where:
    taskId = 1L
    currentLoadBalancer = [
      loadBalancerName   : "example-frontend",
      healthCheck        : [:],
      listenerDescription: [],
      securityGroups     : []
    ]
    targets = [
      [credentials: "test", availabilityZones: ["us-east-1": ["us-east-1c"]]],
      [name: "example-frontend-vpc0", credentials: "test", availabilityZones: ["us-west-2": ["us-west-2a"]]]
    ]
  }
}
