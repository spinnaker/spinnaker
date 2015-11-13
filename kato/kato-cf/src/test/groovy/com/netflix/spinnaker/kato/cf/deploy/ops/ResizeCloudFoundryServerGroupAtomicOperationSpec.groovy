package com.netflix.spinnaker.kato.cf.deploy.ops

import com.netflix.spinnaker.kato.cf.TestCredential
import com.netflix.spinnaker.kato.cf.deploy.description.ResizeCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.cf.security.TestCloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.springframework.web.client.ResourceAccessException
import spock.lang.Specification

class ResizeCloudFoundryServerGroupAtomicOperationSpec extends Specification {

  CloudFoundryOperations client
  CloudFoundryOperations clientForNonExistentServerGroup

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))

    client = Mock(CloudFoundryOperations)

    clientForNonExistentServerGroup = Mock(CloudFoundryOperations)
  }

  void "should not fail resize when server group does not exist"() {
    given:
    1 * clientForNonExistentServerGroup.updateApplicationInstances("my-stack-v000", 10) >> { throw new ResourceAccessException("app doesn't exist") }
    0 * clientForNonExistentServerGroup._

    def op = new ResizeCloudFoundryServerGroupAtomicOperation(
        new ResizeCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            zone: "staging",
            targetSize: 10,
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: clientForNonExistentServerGroup)

    when:
    op.operate([])

    then:
    thrown(Exception)
  }

  void "should resize server group"() {
    setup:
    def op = new ResizeCloudFoundryServerGroupAtomicOperation(
        new ResizeCloudFoundryServerGroupDescription(
            serverGroupName: "my-stack-v000",
            zone: "staging",
            targetSize: 10,
            credentials: TestCredential.named('baz')))
    op.cloudFoundryClientFactory = new TestCloudFoundryClientFactory(stubClient: client)

    when:
    op.operate([])

    then:
    1 * client.updateApplicationInstances("my-stack-v000", 10)
    0 * client._
  }

}
