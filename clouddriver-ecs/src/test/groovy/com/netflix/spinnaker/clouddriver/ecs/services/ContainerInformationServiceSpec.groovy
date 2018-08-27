/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.services

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ecs.model.Container
import com.amazonaws.services.ecs.model.LoadBalancer
import com.amazonaws.services.ecs.model.NetworkBinding
import com.netflix.spinnaker.clouddriver.ecs.cache.client.*
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth
import com.netflix.spinnaker.clouddriver.ecs.security.ECSCredentialsConfig
import spock.lang.Specification
import spock.lang.Subject

class ContainerInformationServiceSpec extends Specification {
  def ecsCredentialsConfig = Mock(ECSCredentialsConfig)
  def taskCacheClient = Mock(TaskCacheClient)
  def serviceCacheClient = Mock(ServiceCacheClient)
  def taskHealthCacheClient = Mock(TaskHealthCacheClient)
  def ecsInstanceCacheClient = Mock(EcsInstanceCacheClient)
  def containerInstanceCacheClient = Mock(ContainerInstanceCacheClient)

  @Subject
  def service = new ContainerInformationService(ecsCredentialsConfig,
    taskCacheClient,
    serviceCacheClient,
    taskHealthCacheClient,
    ecsInstanceCacheClient,
    containerInstanceCacheClient)

  def 'should return a proper health status'() {
    given:
    def taskId = 'task-id'
    def serviceName = 'test-service-name'
    def state = 'Up'
    def type = 'loadbalancer'

    def cachedService = new Service(
      serviceName: serviceName,
      loadBalancers: [new LoadBalancer()]
    )

    def cachedTaskHealth = new TaskHealth(
      taskId: taskId,
      state: state,
      type: type
    )

    serviceCacheClient.get(_) >> cachedService
    taskHealthCacheClient.get(_) >> cachedTaskHealth

    def expectedHealthStatus = [
      [
        instanceId: taskId,
        state     : state,
        type      : type
      ]
    ]

    when:
    def retrievedHealthStatus = service.getHealthStatus(taskId, serviceName, 'test-account', 'us-west-1')

    then:
    retrievedHealthStatus == expectedHealthStatus
  }

  def 'should return a unknown health status when the service cache is null'() {
    given:
    def taskId = 'task-id'
    def serviceName = 'test-service-name'
    def state = 'Up'
    def type = 'loadBalancer'

    def cachedTaskHealth = new TaskHealth(
      taskId: taskId,
      state: state,
      type: type
    )

    serviceCacheClient.get(_) >> null
    taskHealthCacheClient.get(_) >> cachedTaskHealth

    def expectedHealthStatus = [
      [
        instanceId: taskId,
        state     : 'Unknown',
        type      : type
      ]
    ]

    when:
    def retrievedHealthStatus = service.getHealthStatus(taskId, serviceName, 'test-account', 'us-west-1')

    then:
    retrievedHealthStatus == expectedHealthStatus
  }

  def 'should return a unknown health status when the task cache is null'() {
    given:
    def taskId = 'task-id'
    def serviceName = 'test-service-name'
    def type = 'loadBalancer'

    def cachedService = new Service(
      serviceName: serviceName,
      loadBalancers: [new LoadBalancer()]
    )

    serviceCacheClient.get(_) >> cachedService
    taskHealthCacheClient.get(_) >> null

    def expectedHealthStatus = [
      [
        instanceId: taskId,
        state     : 'Unknown',
        type      : type
      ]
    ]

    when:
    def retrievedHealthStatus = service.getHealthStatus(taskId, serviceName, 'test-account', 'us-west-1')

    then:
    retrievedHealthStatus == expectedHealthStatus
  }

  def 'should throw an exception when the service has multiple loadbalancers'() {
    given:
    def cachedService = new Service(
      loadBalancers: [new LoadBalancer(), new LoadBalancer()]
    )

    serviceCacheClient.get(_) >> cachedService
    taskHealthCacheClient.get(_) >> new TaskHealth()

    when:
    service.getHealthStatus('task-id', 'test-service-name', 'test-account', 'us-west-1')

    then:
    IllegalArgumentException exception = thrown()
    exception.message == 'Cannot have more than 1 load balancer while checking ECS health.'
  }

  def 'should return a proper private address for a task'() {
    given:
    def account = 'test-account'
    def region = 'us-west-1'
    def containerInstanceArn = 'container-instance-arn'
    def ip = '127.0.0.1'
    def port = 1337

    def ecsAccount = new ECSCredentialsConfig.Account(
      name: account,
      awsAccount: 'aws-' + account
    )

    def task = new Task(
      containerInstanceArn: containerInstanceArn,
      containers: [
        new Container(
          networkBindings: [
            new NetworkBinding(
              hostPort: port
            )
          ]
        )
      ]
    )

    def containerInstance = new ContainerInstance(
      ec2InstanceId: 'i-deadbeef'
    )

    def instance = new Instance(
      privateIpAddress: ip
    )

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsInstanceCacheClient.find(_, _, _) >> [instance]
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]

    when:
    def retrievedIp = service.getTaskPrivateAddress(account, region, task)

    then:
    retrievedIp == ip + ':' + port
  }

  def 'should return a null when port is out of range'() {
    given:
    def task = new Task(
      containers: [
        new Container(
          networkBindings: [
            new NetworkBinding(
              hostPort: 999999999999
            )
          ]
        )
      ]
    )

    when:
    def retrievedIp = service.getTaskPrivateAddress('test-account', 'us-west-1', task)

    then:
    retrievedIp == null
  }

  def 'should return a unknown when there is no container instance for the task'() {
    given:
    def task = new Task(
      containerInstanceArn: 'container-instance-arn',
      containers: [
        new Container(
          networkBindings: [
            new NetworkBinding(
              hostPort: 1337
            )
          ]
        )
      ]
    )

    containerInstanceCacheClient.get(_) >> null

    when:
    def retrievedIp = service.getTaskPrivateAddress('test-account', 'us-west-1', task)

    then:
    retrievedIp == null
  }

  def 'should return a unknown when there is no ec2 instance for the container'() {
    given:
    def task = new Task(
      containerInstanceArn: 'container-instance-arn',
      containers: [
        new Container(
          networkBindings: [
            new NetworkBinding(
              hostPort: 1337
            )
          ]
        )
      ]
    )

    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'test-account',
      awsAccount: 'aws-test-account'
    )

    def containerInstance = new ContainerInstance(
      ec2InstanceId: 'i-deadbeef'
    )

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsInstanceCacheClient.find(_, _, _) >> []
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]

    when:
    def retrievedIp = service.getTaskPrivateAddress('test-account', 'us-west-1', task)

    then:
    retrievedIp == null
  }

  def 'should throw an exception when task has multiple containers'() {
    given:
    def task = new Task(
      containers: [new Container(), new Container(), new Container()]
    )

    when:
    service.getTaskPrivateAddress('test-account', 'region', task)

    then:
    IllegalArgumentException exception = thrown()
    exception.message == 'Multiple containers for a task is not supported.'
  }

  def 'should throw an exception when container has multiple ec2 instances'() {
    given:
    def task = new Task(
      containerInstanceArn: 'container-instance-arn',
      containers: [
        new Container(
          networkBindings: [
            new NetworkBinding(
              hostPort: 1337
            )
          ]
        )
      ]
    )

    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'test-account',
      awsAccount: 'aws-test-account'
    )

    def containerInstance = new ContainerInstance(
      ec2InstanceId: 'i-deadbeef'
    )

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsInstanceCacheClient.find(_, _, _) >> [
      new Instance(instanceId: "id-1"),
      new Instance(instanceId: "id-2")
    ]
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]

    when:
    service.getTaskPrivateAddress('test-account', 'us-west-1', task)

    then:
    IllegalArgumentException exception = thrown()
    exception.message == 'There cannot be more than 1 EC2 container instance for a given region and instance ID.'
  }

  def 'should return a cluster name'() {
    given:
    def originalClusterName = 'test-cluster'
    def serviceName = 'test-service'
    def cachedService = new Service(
      serviceName: serviceName,
      clusterName: originalClusterName
    )

    serviceCacheClient.get(_) >> cachedService

    when:
    String returnedClusterName = service.getClusterName(serviceName, 'test-account', 'us-west-1')

    then:
    returnedClusterName == originalClusterName
  }

  def 'should return a cluster arn'() {
    given:
    def originalClusterArn = 'test-arn'
    def task = new Task(
      clusterArn: originalClusterArn
    )

    taskCacheClient.get(_) >> task

    when:
    String returnedClusterArn = service.getClusterArn('test-account', 'us-west-1', 'task-id')

    then:
    returnedClusterArn == originalClusterArn
  }

  def 'should return an ec2 instance'() {
    given:
    def task = new Task(containerInstanceArn: 'container-instance-arn')
    def containerInstance = new ContainerInstance(ec2InstanceId: 'i-deadbeef')
    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'ecs-account',
      awsAccount: 'aws-test-account'
    )
    def givenInstance = new Instance(
      instanceId: 'i-deadbeef',
      privateIpAddress: '0.0.0.0',
      publicIpAddress: '127.0.0.1'
    )

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]
    ecsInstanceCacheClient.find(_, _, _) >> [givenInstance]


    when:
    def retrievedInstance = service.getEc2Instance('ecs-account', 'us-west-1', task)

    then:
    retrievedInstance == givenInstance
  }

  def 'should return an null when getting ec2 instance without a container instance'() {
    given:
    def task = new Task(containerInstanceArn: 'container-instance-arn')
    containerInstanceCacheClient.get(_) >> null


    when:
    def retrievedInstance = service.getEc2Instance('ecs-account', 'us-west-1', task)

    then:
    retrievedInstance == null
  }

  def 'should return an when no ec2 instances are cached'() {
    given:
    def task = new Task(containerInstanceArn: 'container-instance-arn')
    def containerInstance = new ContainerInstance(ec2InstanceId: 'i-deadbeef')
    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'ecs-account',
      awsAccount: 'aws-test-account'
    )

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]
    ecsInstanceCacheClient.find(_, _, _) >> []


    when:
    def retrievedInstance = service.getEc2Instance('ecs-account', 'us-west-1', task)

    then:
    retrievedInstance == null
  }

  def 'should throw an exception when multiple ec2 instances are found for one id'() {
    given:
    def task = new Task(containerInstanceArn: 'container-instance-arn')
    def containerInstance = new ContainerInstance(ec2InstanceId: 'i-deadbeef')
    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'ecs-account',
      awsAccount: 'aws-test-account'
    )
    def givenInstances = []
    0.upto(4, {
      givenInstances << new Instance(
        instanceId: "i-deadbee${it}",
        privateIpAddress: '0.0.0.0',
        publicIpAddress: '127.0.0.1'
      )
    })

    containerInstanceCacheClient.get(_) >> containerInstance
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]
    ecsInstanceCacheClient.find(_, _, _) >> givenInstances


    when:
    service.getEc2Instance('ecs-account', 'us-west-1', task)

    then:
    IllegalArgumentException exception = thrown()
    exception.message == 'There cannot be more than 1 EC2 container instance for a given region and instance ID.'
  }

  def 'should return an aws account name'(){
    given:
    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'ecs-account',
      awsAccount: 'aws-test-account'
    )
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]

    when:
    def retrievedAccountName = service.getAwsAccountName(ecsAccount.getName())

    then:
    retrievedAccountName == ecsAccount.getAwsAccount()
  }


  def 'should return an null when no aws account is found associated to the ecs account'(){
    given:
    def ecsAccount = new ECSCredentialsConfig.Account(
      name: 'ecs-account',
      awsAccount: 'aws-test-account'
    )
    ecsCredentialsConfig.getAccounts() >> [ecsAccount]

    when:
    def retrievedAccountName = service.getAwsAccountName('wrong-account')

    then:
    retrievedAccountName == null
  }
}
