package com.netflix.kato.deploy

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
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
