/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import spock.lang.Specification

class AmiIdResolverSpec extends Specification {

  private String amiId = 'ami-12345'
  private String accountId = '98765'

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "looks up AMI in three ways in order"() {
    setup:
    def ec2 = Mock(AmazonEC2)

    when:
    AmiIdResolver.resolveAmiIdFromAllSources(ec2, 'us-east-1', amiId, accountId)

    then:
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
      assert dir.imageIds == [amiId]
      assert dir.owners == []
      assert dir.executableUsers == [accountId]
    }

    then:
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
      assert dir.imageIds == [amiId]
      assert dir.owners == [accountId]
      assert dir.executableUsers == []
    }

    then:
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
      assert dir.imageIds == [amiId]
      assert dir.owners == []
      assert dir.executableUsers == []
    }
  }
}
