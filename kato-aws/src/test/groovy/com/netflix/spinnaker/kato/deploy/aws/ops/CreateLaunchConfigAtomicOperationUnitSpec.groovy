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
package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.autoscaling.model.InstanceMonitoring
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.CreateLaunchConfigDescription
import com.netflix.spinnaker.kato.model.aws.LaunchConfigurationOptions
import spock.lang.Specification

class CreateLaunchConfigAtomicOperationUnitSpec extends Specification {

  def mockAmazonAutoScaling = Mock(AmazonAutoScaling)
  def mockAmazonClientProvider = Mock(AmazonClientProvider)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    with(mockAmazonClientProvider) {
      getAutoScaling(_, _) >> mockAmazonAutoScaling
    }
  }

  void "operation invokes create launch configuration"() {
    setup:
    def description = new CreateLaunchConfigDescription(asgName: "wolverine", regions: ["us-west-1"],
        launchConfigOptions: new LaunchConfigurationOptions(launchConfigurationName: "ironman")
    )
    description.credentials = new AmazonCredentials(Mock(AWSCredentials), "baz")
    def operation = new CreateLaunchConfigAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAmazonAutoScaling.createLaunchConfiguration(new CreateLaunchConfigurationRequest(
      launchConfigurationName: "ironman",
      securityGroups: [],
      blockDeviceMappings: [],
      instanceMonitoring: new InstanceMonitoring(enabled: false)
    ))
    0 * mockAmazonAutoScaling._
  }
}
