/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling
import com.amazonaws.services.ecs.AmazonECS
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class CommonAtomicOperation extends Specification{
  def amazonClientProvider = Mock(AmazonClientProvider)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def containerInformationService = Mock(ContainerInformationService)
  def ecs = Mock(AmazonECS)
  def autoscaling = Mock(AWSApplicationAutoScaling)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }
}
