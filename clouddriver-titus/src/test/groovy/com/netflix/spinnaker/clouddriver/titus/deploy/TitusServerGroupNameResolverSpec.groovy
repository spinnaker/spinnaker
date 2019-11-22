/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy

import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import spock.lang.Specification
import spock.lang.Unroll


class TitusServerGroupNameResolverSpec extends Specification {

  def titusClient = Mock(TitusClient)
  def region = 'us-west-1'

  void setup() {
    Task task = new DefaultTask("task")
    TaskRepository.threadLocalTask.set(task)
  }

  @Unroll
  void "should correctly resolve next sequence number when details look like a sequence number - i.e., v([0-9]+)"() {
    given:
    def resolver = new TitusServerGroupNameResolver(titusClient, region)

    def application = 'application'
    def stack = 'stack'
    def serverGroupName = "$application-$stack-$details-$sequence"
    def nextServerGroupName = "$application-$stack-$details-$nextSequence"

    List<Job> jobs =
      [
        new Job(
          name: serverGroupName
        )
      ]
    titusClient.findJobsByApplication(application) >> jobs

    when:
    def result = resolver.resolveNextServerGroupName(application, stack, details, false)

    then:
    result == nextServerGroupName

    where:
    details      | sequence | nextSequence
    "v00001"     |  "v000"  | "v001"
    "v82589065"  |  "v000"  | "v001"
    "v82589065"  |  "v001"  | "v002"
    "v82589065"  |  "v998"  | "v999"
    "v8258c06b"  |  "v000"  | "v001" //one a-z0-9 test for good measure
  }

  void "should rollover sequence number"() {
    given:
    def resolver = new TitusServerGroupNameResolver(titusClient, region)

    def application = 'application'
    def stack = 'v000' // unlikely this would be a stack name, but it's a good test case
    def serverGroupName = "$application-$stack-v999"
    def nextServerGroupName = "$application-$stack-v000"

    List<Job> jobs =
      [
        new Job(
          name: serverGroupName
        )
      ]
    titusClient.findJobsByApplication(application) >> jobs

    when:
    def result = resolver.resolveNextServerGroupName(application, stack, null, false)

    then:
    result == nextServerGroupName
  }
}
