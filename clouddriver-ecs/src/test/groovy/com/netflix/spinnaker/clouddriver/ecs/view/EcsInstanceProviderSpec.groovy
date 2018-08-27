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

package com.netflix.spinnaker.clouddriver.ecs.view

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject

class EcsInstanceProviderSpec extends Specification {
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def amazonClientProvider = Mock(AmazonClientProvider)
  def containerInformationService = Mock(ContainerInformationService)
  def taskCacheClient = Mock(TaskCacheClient)
  def containerInstanceCacheClient = Mock(ContainerInstanceCacheClient)

  @Subject
  def provider = new EcsInstanceProvider(containerInformationService, taskCacheClient,
                                         containerInstanceCacheClient)

  def 'should return an EcsTask'() {
    given:
    def region = 'us-west-1'
    def account = 'test-account'
    def taskId = 'deadbeef-94f3-4994-8e81-339c4d1be1ba'
    def taskArn = 'arn:aws:ecs:' + region + ':123456789012:task/' + taskId
    def address = '127.0.0.1:1337'
    def startTime = System.currentTimeMillis()

    def netflixAmazonCredentials = Mock(NetflixAmazonCredentials)
    def awsCredentialsProvider = Mock(AWSCredentialsProvider)
    def amazonEC2 = Mock(AmazonEC2)

    def task = new Task(
      taskId: taskId,
      taskArn: taskArn,
      lastStatus: 'RUNNING',
      desiredStatus: 'RUNNING',
      startedAt: startTime,
    )

    def containerInstance =  new ContainerInstance()

    def ecsTask = new EcsTask(taskId, startTime, 'RUNNING', 'RUNNING',
      null, null, address, null)

    taskCacheClient.get(_) >> task
    accountCredentialsProvider.getCredentials(_) >> netflixAmazonCredentials
    netflixAmazonCredentials.getCredentialsProvider() >> awsCredentialsProvider
    amazonClientProvider.getAmazonEC2(_, _, _) >> amazonEC2
    containerInstanceCacheClient.get(_) >> containerInstance
    containerInformationService.getTaskPrivateAddress(_, _, _) >> address

    when:
    def taskInstance = provider.getInstance(account, region, taskId)

    then:
    taskInstance == ecsTask
  }
}
