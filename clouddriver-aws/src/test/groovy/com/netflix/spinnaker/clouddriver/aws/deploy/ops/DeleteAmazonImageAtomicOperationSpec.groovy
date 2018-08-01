/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DeregisterImageRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonImageDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class DeleteAmazonImageAtomicOperationSpec extends Specification {
  def credentials = Stub(NetflixAmazonCredentials) {
    getName() >> "test"
  }

  def description = new DeleteAmazonImageDescription(
    imageId: "ami-123",
    region: "us-east-1",
    credentials: credentials
  )

  @Subject
  def deleteImageOp = new DeleteAmazonImageAtomicOperation(description)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def "should deregister an image"() {
    given:
    def ec2 = Mock(AmazonEC2)
    def amazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(credentials, "us-east-1") >> ec2
    }

    and:
    deleteImageOp.amazonClientProvider = amazonClientProvider

    when:
    deleteImageOp.operate([])

    then:
    1 * ec2.deregisterImage(_) >> { DeregisterImageRequest request ->
      assert request.imageId == description.imageId
    }
  }
}
