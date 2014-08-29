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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DeleteTagsRequest
import com.amazonaws.services.ec2.model.DescribeTagsResult
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.TagDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAamzonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.AllowLaunchDescription
import spock.lang.Specification

class AllowLaunchAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "image attribute modification is invoked on request"() {
    setup:
    def provider = Mock(AmazonClientProvider)
    def ec2 = Mock(AmazonEC2) {
      describeTags(_) >> new DescribeTagsResult()
    }
    provider.getAmazonEC2(_, _) >> ec2
    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: Mock(NetflixAssumeRoleAamzonCredentials))
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    def accountHolder = Mock(AccountCredentialsProvider)
    op.accountCredentialsProvider = accountHolder

    when:
    op.operate([])

    then:
    1 * ec2.modifyImageAttribute(_) >> { ModifyImageAttributeRequest request ->
      assert request.launchPermission.add.get(0).userId == "5678"
    }
    1 * accountHolder.getCredentials("prod") >> {
      def mock = Mock(NetflixAssumeRoleAamzonCredentials)
      mock.getAccountId() >> 5678
      mock
    }
  }

  void "should replicate tags"() {
    def prodCredentials = new NetflixAssumeRoleAamzonCredentials(name: "prod")
    def testCredentials = new NetflixAssumeRoleAamzonCredentials(name: "test")

    def sourceAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)
    def provider = Mock(AmazonClientProvider)

    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider){
      1 * getCredentials("prod") >> prodCredentials
    }
    with(provider) {
      1 * getAmazonEC2(testCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2(prodCredentials, _) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([b:"1", c: "2"])
      1 * deleteTags(new DeleteTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b"), new Tag(key: "c")]))
      1 * createTags(new CreateTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "a", value: "1"), new Tag(key: "b", value: "2")]))
    }
  }

  Closure<DescribeTagsResult> constructDescribeTagsResult = { Map tags ->
    new DescribeTagsResult(tags: tags.collect {new TagDescription(key: it.key, value: it.value) })
  }
}
