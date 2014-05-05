package com.netflix.kato.deploy.aws.ops

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.StaticAmazonClients
import com.netflix.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.kato.security.aws.AmazonCredentials
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class ShrinkClusterAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation looks up unused asgs and deletes them"() {
    setup:
      def mockAutoScaling = Mock(AmazonAutoScaling)
      StaticAmazonClients.metaClass.'static'.getAutoScaling = { AmazonCredentials credentials, String region ->
        mockAutoScaling
      }
      def description = new ShrinkClusterDescription()
      description.application = "asgard"
      description.clusterName = "asgard-test"
      description.regions = ['us-west-1']
      description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
      def inactiveClusterName = "asgard-test-v000"
      def rt = Mock(RestTemplate)
      def eddaAsgUrl = "http://entrypoints-v2.us-west-1.baz.netflix.net:7001/REST/v2/aws/autoScalingGroups"
      rt.getForEntity(eddaAsgUrl, List) >> {
        def mock = Mock(ResponseEntity)
        mock.getBody() >> { [inactiveClusterName] }
        mock
      }
      rt.getForEntity("$eddaAsgUrl/$inactiveClusterName", Map) >> {
        def mock = Mock(ResponseEntity)
        mock.getBody() >> { [instances: []] }
        mock
      }
      def operation = new ShrinkClusterAtomicOperation(description, rt)

    when:
      operation.operate([])

    then:
      1 * mockAutoScaling.deleteAutoScalingGroup(_) >> { DeleteAutoScalingGroupRequest request ->
        assert request.autoScalingGroupName == inactiveClusterName
        assert request.forceDelete
      }
  }
}
