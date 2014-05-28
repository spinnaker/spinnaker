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

package com.netflix.spinnaker.kato.deploy.aws.handlers

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.AutoScalingWorker
import com.netflix.spinnaker.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerResult
import spock.lang.Shared
import spock.lang.Specification

class BasicAmazonDeployHandlerUnitSpec extends Specification {

  @Shared
  BasicAmazonDeployHandler handler

  @Shared
  Task task

  def setupSpec() {
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, _) >> Mock(AmazonAutoScaling)
    mockAmazonClientProvider.getAmazonEC2(_, _) >> Mock(AmazonEC2)
    KatoAWSConfig.AwsConfigurationProperties awsConfigurationProperties = new KatoAWSConfig.AwsConfigurationProperties()
    awsConfigurationProperties.defaults.iamRole = "IamRole"
    awsConfigurationProperties.defaults.keyPair = "keypair"
    this.handler = new BasicAmazonDeployHandler(amazonClientProvider: mockAmazonClientProvider, awsConfigurationProperties: awsConfigurationProperties)
    this.task = Mock(Task)
    this.task.getResultObjects() >> []
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
    AutoScalingWorker.metaClass.deploy = {}
    AutoScalingWorker.metaClass.setLoadBalancers = { setlbCalls++ }
    def description = new BasicAmazonDeployDescription()
    description.availabilityZones = ["us-east-1": []]
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")

    when:
    handler.handle(description, [new CreateAmazonLoadBalancerResult(loadBalancers: ["us-east-1": new CreateAmazonLoadBalancerResult.LoadBalancer("lb", "lb1.nflx")])])

    then:
    setlbCalls
  }
}
