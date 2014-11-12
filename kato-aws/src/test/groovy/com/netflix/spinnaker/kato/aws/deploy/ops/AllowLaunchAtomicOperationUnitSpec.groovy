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


package com.netflix.spinnaker.kato.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DeleteTagsRequest
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.DescribeTagsResult
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.TagDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.aws.deploy.description.AllowLaunchDescription
import spock.lang.Specification

class AllowLaunchAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "image amiId is resolved from name"() {
    setup:
    def ec2 = Mock(AmazonEC2)
    def provider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _) >> ec2
    }

    def creds = Stub(AccountCredentialsProvider) {
      getCredentials(_) >> Stub(NetflixAssumeRoleAmazonCredentials)
    }
    def op = new AllowLaunchAtomicOperation(new AllowLaunchDescription(amiName: 'super-awesome-ami'))
    op.accountCredentialsProvider = creds
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    ec2.describeTags(_) >> new DescribeTagsResult()
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
        assert dir.filters
        assert dir.filters.size() == 1
        assert dir.filters.first().name == 'name'
        assert dir.filters.first().values == ['super-awesome-ami']

        new DescribeImagesResult().withImages(new Image().withImageId('ami-12345'))
    }
  }

  void "image attribute modification is invoked on request"() {
    setup:
    def ec2 = Mock(AmazonEC2) {
      describeTags(_) >> new DescribeTagsResult()
    }
    def provider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _) >> ec2
    }
    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: Mock(NetflixAssumeRoleAmazonCredentials))
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
      def mock = Mock(NetflixAssumeRoleAmazonCredentials)
      mock.getAccountId() >> 5678
      mock
    }
  }

  void "should replicate tags"() {
    def prodCredentials = new NetflixAssumeRoleAmazonCredentials(name: "prod")
    def testCredentials = new NetflixAssumeRoleAmazonCredentials(name: "test")

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

  void "should replicate tags from the same account"() {
    def testCredentials = new NetflixAssumeRoleAmazonCredentials(name: "test")

    def sourceAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)
    def provider = Mock(AmazonClientProvider)

    def description = new AllowLaunchDescription(account: "test", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider){
      1 * getCredentials("test") >> testCredentials
    }
    with(provider) {
      2 * getAmazonEC2(testCredentials, _) >> sourceAmazonEc2  >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
      1 * deleteTags(new DeleteTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "a"), new Tag(key: "b")]))
      1 * createTags(new CreateTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "a", value: "1"), new Tag(key: "b", value: "2")]))
    }
  }

  Closure<DescribeTagsResult> constructDescribeTagsResult = { Map tags ->
    new DescribeTagsResult(tags: tags.collect {new TagDescription(key: it.key, value: it.value) })
  }
}
