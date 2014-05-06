package com.netflix.kato.deploy.aws.ops.dns

import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesResult
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.StaticAmazonClients
import com.netflix.kato.deploy.aws.description.UpsertAmazonDNSDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerResult
import com.netflix.kato.security.aws.AmazonCredentials
import spock.lang.Specification

class UpsertAmazonDNSAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation inherits prior-deployed elb dns name when no target explicitly specified"() {
    setup:
      def mockClient = Mock(AmazonRoute53)
      StaticAmazonClients.metaClass.'static'.getAmazonRoute53 = { AmazonCredentials credentials, String region ->
        mockClient
      }
      def op = new UpsertAmazonDNSAtomicOperation(new UpsertAmazonDNSDescription())
      def elbDeploy = Mock(CreateAmazonLoadBalancerResult)
      elbDeploy.getLoadBalancers() >> ["us-east-1": new CreateAmazonLoadBalancerResult.LoadBalancer("foo", "elb")]

    when:
      op.operate([elbDeploy])

    then:
      1 * mockClient.listHostedZones() >> {
        def zone = Mock(HostedZone)
        zone.getId() >> "1234"
        def result = Mock(ListHostedZonesResult)
        result.getHostedZones() >> [zone]
        result
      }
      1 * mockClient.changeResourceRecordSets(_) >> { ChangeResourceRecordSetsRequest request ->
        assert request.changeBatch.changes.first().resourceRecordSet.resourceRecords.first().value == "elb"
      }

  }

  void "operation calls out to route53 to UPSERT dns record"() {
    setup:
      def mockClient = Mock(AmazonRoute53)
      StaticAmazonClients.metaClass.'static'.getAmazonRoute53 = { AmazonCredentials credentials, String region ->
        mockClient
      }
      def op = new UpsertAmazonDNSAtomicOperation(new UpsertAmazonDNSDescription())

    when:
      op.operate([])

    then:
      1 * mockClient.listHostedZones() >> {
        def zone = Mock(HostedZone)
        zone.getId() >> "1234"
        def result = Mock(ListHostedZonesResult)
        result.getHostedZones() >> [zone]
        result
      }
      1 * mockClient.changeResourceRecordSets(_)

  }
}
