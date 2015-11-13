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

package com.netflix.spinnaker.kato.aws.deploy.ops.dns

import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesResult
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonDNSDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import spock.lang.Specification

class UpsertAmazonDNSAtomicOperationSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "operation inherits prior-deployed elb dns name when no target explicitly specified"() {
    setup:
    def mockClient = Mock(AmazonRoute53)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonRoute53(_, _, true) >> mockClient
    def op = new UpsertAmazonDNSAtomicOperation(new UpsertAmazonDNSDescription())
    op.amazonClientProvider = mockAmazonClientProvider
    def elbDeploy = Mock(UpsertAmazonLoadBalancerResult)
    elbDeploy.getLoadBalancers() >> ["us-east-1": new UpsertAmazonLoadBalancerResult.LoadBalancer("foo", "elb")]

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
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAmazonRoute53(_, _, true) >> mockClient
    def op = new UpsertAmazonDNSAtomicOperation(new UpsertAmazonDNSDescription())
    op.amazonClientProvider = mockAmazonClientProvider

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
