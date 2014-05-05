package com.netflix.kato.deploy.aws.handlers

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.AutoScalingWorker
import com.netflix.kato.deploy.aws.StaticAmazonClients
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateLoadBalancerResult
import com.netflix.kato.security.aws.AmazonCredentials
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployHandlerUnitSpec extends Specification {

  @Shared
  BasicAmazonDeployHandler handler

  @Shared
  Task task

  def setupSpec() {
    this.handler = new BasicAmazonDeployHandler()
    this.task = Mock(Task)
    TaskRepository.threadLocalTask.set(task)
  }

  void "handler supports basic deploy description type"() {
    given:
      def description = new BasicAmazonDeployDescription()

    expect:
      handler.handles description
  }

  void "handler invokes a deploy feature for each specified region"() {
    setup:
      def deployCallCounts = 0
      AutoScalingWorker.metaClass.deploy = { deployCallCounts++; "foo" }
      StaticAmazonClients.metaClass.'static'.getAmazonEC2 = { String accessId, String secretKey, String region -> Mock(AmazonEC2) }
      StaticAmazonClients.metaClass.'static'.getAutoScaling = { String accessId, String secretKey, String region -> Mock(AmazonAutoScaling) }
      def description = new BasicAmazonDeployDescription()
      description.availabilityZones = ["us-west-1": [], "us-east-1": []]
      description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")

    when:
      def results = handler.handle(description, [])

    then:
      2 == deployCallCounts
      results.serverGroupNames == ['us-west-1:foo', 'us-east-1:foo']
  }

  void "load balancer names are derived from prior execution results"() {
    setup:
      def setlbCalls = 0
      AutoScalingWorker.metaClass.deploy = { }
      AutoScalingWorker.metaClass.setLoadBalancers = { setlbCalls++ }
      StaticAmazonClients.metaClass.'static'.getAmazonEC2 = { String accessId, String secretKey, String region -> Mock(AmazonEC2) }
      StaticAmazonClients.metaClass.'static'.getAutoScaling = { String accessId, String secretKey, String region -> Mock(AmazonAutoScaling) }
      def description = new BasicAmazonDeployDescription()
      description.availabilityZones = ["us-east-1": []]
      description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")

    when:
      handler.handle(description, [new CreateLoadBalancerResult(loadBalancers: ["us-east-1": new CreateLoadBalancerResult.LoadBalancer("lb", "lb1.nflx")])])

    then:
      setlbCalls
  }
}
