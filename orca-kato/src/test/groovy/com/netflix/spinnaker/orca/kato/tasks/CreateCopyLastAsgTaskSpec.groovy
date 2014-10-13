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

package com.netflix.spinnaker.orca.kato.tasks
import spock.lang.Specification
import spock.lang.Subject
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.api.TaskId
import rx.Observable

class CreateCopyLastAsgTaskSpec extends Specification {
    @Subject task = new CreateCopyLastAsgTask()
    def context = new SimpleTaskContext()
    def mapper = new ObjectMapper()
    def taskId = new TaskId(UUID.randomUUID().toString())

    //The minimum required fields to copyLastAsg
    Map<String, Object> copyLastAsgConfig = [
        application      : "hodor",
        availabilityZones: ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]],
        credentials      : "fzlem"
    ]

    def setup() {
        mapper.registerModule(new GuavaModule())

        task.mapper = mapper

        copyLastAsgConfig.each {
            context."copyLastAsg.$it.key" = it.value
        }
    }

    def "creates a deployment based on job parameters"() {
        given:
        def operations
        task.kato = Mock(KatoService) {
            1 * requestOperations(*_) >> {
                operations = it[0]
                Observable.from(taskId)
            }
        }

        when:
        task.execute(context)

        then:
        operations.size() == 3
        operations[2].copyLastAsgDescription.amiName == null
        operations[2].copyLastAsgDescription.application == "hodor"
        operations[2].copyLastAsgDescription.availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
        operations[2].copyLastAsgDescription.credentials == "fzlem"
    }

    def "can include optional parameters"() {
        given:
        context."copyLastAsg.instanceType" = "t1.megaBig"
        context."copyLastAsg.stack" = "hodork"

        def operations
        task.kato = Mock(KatoService) {
            1 * requestOperations(*_) >> {
                operations = it[0]
                Observable.from(taskId)
            }
        }

        when:
        task.execute(context)

        then:
        operations.size() == 3
        with(operations[2].copyLastAsgDescription) {
          amiName == null
          application == "hodor"
          availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
          credentials == "fzlem"
          instanceType == "t1.megaBig"
          stack == "hodork"
        }
    }

    def "amiName prefers value from context over bake input"() {
        given:
        context."copyLastAsg.amiName" = "ami-696969"
        context."bake.ami" = "ami-soixante-neuf"


        def operations
        task.kato = Mock(KatoService) {
            1 * requestOperations(*_) >> {
                operations = it[0]
                Observable.from(taskId)
            }
        }

        when:
        task.execute(context)

        then:
        operations.size() == 3
        with(operations[2].copyLastAsgDescription) {
          amiName == "ami-696969"
          application == "hodor"
          availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
          credentials == "fzlem"
        }
    }

    def "amiName uses value from bake"() {
        given:
        context."bake.ami" = "ami-soixante-neuf"


        def operations
        task.kato = Mock(KatoService) {
            1 * requestOperations(*_) >> {
                operations = it[0]
                Observable.from(taskId)
            }
        }

        when:
        task.execute(context)

        then:
        operations.size() == 3
        with(operations[2].copyLastAsgDescription) {
          amiName == "ami-soixante-neuf"
          application == "hodor"
          availabilityZones == ["us-east-1": ["a", "d"], "us-west-1": ["a", "b"]]
          credentials == "fzlem"
        }
    }

    def "calls allowlaunch prior to copyLast"() {
      given:
      context."bake.ami" = amiName


      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations(*_) >> {
          operations = it[0]
          Observable.from(taskId)
        }
      }

      when:
      task.execute(context)

      then:
      operations.size() == 3
      operations[0].allowLaunchDescription.amiName == amiName
      operations[0].allowLaunchDescription.region == "us-east-1"
      operations[1].allowLaunchDescription.amiName == amiName
      operations[1].allowLaunchDescription.region == "us-west-1"
      operations[2].copyLastAsgDescription.amiName == amiName

      where:
      amiName = "ami-soixante-neuf"
    }
}
