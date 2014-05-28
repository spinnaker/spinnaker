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

package com.netflix.bluespar.kato.deploy.aws.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.bluespar.kato.data.task.Task
import com.netflix.bluespar.kato.data.task.TaskRepository
import com.netflix.bluespar.kato.deploy.aws.description.AllowLaunchDescription
import com.netflix.bluespar.kato.security.NamedAccountCredentialsHolder
import com.netflix.bluespar.kato.security.aws.AmazonRoleAccountCredentials
import spock.lang.Specification

class AllowLaunchAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "image attribute modification is invoked on request"() {
    setup:
    def provider = Mock(AmazonClientProvider)
    def ec2 = Mock(AmazonEC2)
    provider.getAmazonEC2(_, _) >> ec2
    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: Mock(AmazonCredentials))
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    def accountHolder = Mock(NamedAccountCredentialsHolder)
    op.namedAccountCredentialsHolder = accountHolder

    when:
    op.operate([])

    then:
    1 * ec2.modifyImageAttribute(_) >> { ModifyImageAttributeRequest request ->
      assert request.launchPermission.add.get(0).userId == "5678"
    }
    1 * accountHolder.getCredentials("prod") >> {
      def mock = Mock(AmazonRoleAccountCredentials)
      mock.getAccountId() >> "5678"
      mock
    }
  }
}
