/*
 * Copyright 2016 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.data.task.*
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.cloudfoundry.client.lib.domain.CloudDomain
import spock.lang.Specification

class UpsertCloudFoundryLoadBalancerAtomicOperationSpec extends Specification {

  Task task

  CloudFoundryOperations client

  def setup() {
    task = new DefaultTask('test')
    TaskRepository.threadLocalTask.set(task)

    client = Mock(CloudFoundryOperations)
  }

  void "handles failure to create load balancer"() {
    def op = new UpsertCloudFoundryLoadBalancerAtomicOperation(
        new UpsertCloudFoundryLoadBalancerDescription(
            loadBalancerName: "my-load-balancer",
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)

    when:
    op.operate([])

    then:
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'cfapps.io', null)}
    1 * client.addRoute('my-load-balancer', 'cfapps.io') >> {
      throw new RuntimeException('Simulated CF failure')
    }
    0 * client._

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'UPSERT_LOAD_BALANCER', status:'Initializing creation of load balancer my-load-balancer in staging...', state:'STARTED')),
    ]

    Exception e = thrown()
    e.message == "Simulated CF failure"
  }

  void "should create load balancer"() {
    given:
    def op = new UpsertCloudFoundryLoadBalancerAtomicOperation(
        new UpsertCloudFoundryLoadBalancerDescription(
            loadBalancerName: "my-load-balancer",
            region: "staging",
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)

    when:
    op.operate([])

    then:
    1 * client.getDefaultDomain() >> { new CloudDomain(null, 'cfapps.io', null)}
    1 * client.addRoute('my-load-balancer', 'cfapps.io')
    0 * client._

    task.history == [
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'INIT', status:'Creating task test', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'UPSERT_LOAD_BALANCER', status:'Initializing creation of load balancer my-load-balancer in staging...', state:'STARTED')),
        new TaskDisplayStatus(new DefaultTaskStatus(phase:'UPSERT_LOAD_BALANCER', status:'Done creating load balancer my-load-balancer.', state:'STARTED')),
    ]
  }

}
