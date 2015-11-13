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

package com.netflix.spinnaker.kato.deploy

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class DeployAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "deploy handler is retrieved from registry"() {
    setup:
    def deployHandlerRegistry = Mock(DeployHandlerRegistry)
    def testDeployHandler = Mock(DeployHandler)
    def deployAtomicOperation = new DeployAtomicOperation(Mock(DeployDescription))
    deployAtomicOperation.deploymentHandlerRegistry = deployHandlerRegistry

    when:
    deployAtomicOperation.operate([])

    then:
    1 * deployHandlerRegistry.findHandler(_) >> testDeployHandler
    1 * testDeployHandler.handle(_, _) >> { Mock(DeploymentResult) }
  }

  void "exception is thrown when handler doesnt exist in registry"() {
    setup:
    def deployHandlerRegistry = Mock(DeployHandlerRegistry)
    def deployAtomicOperation = new DeployAtomicOperation(Mock(DeployDescription))
    deployAtomicOperation.deploymentHandlerRegistry = deployHandlerRegistry

    when:
    deployAtomicOperation.operate([])

    then:
    1 * deployHandlerRegistry.findHandler(_) >> null
    thrown DeployHandlerNotFoundException
  }
}
