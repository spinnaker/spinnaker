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
import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class AllowLaunchAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "image amiId is resolved from name"() {
    setup:
    def ec2 = Mock(AmazonEC2)
    def provider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _, true) >> ec2
    }

    def target = Stub(NetflixAmazonCredentials) {
      getAccountId() >> '12345'
    }

    def source = Stub(NetflixAmazonCredentials) {
      getAccountId() >> '67890'
    }


    def creds = Stub(AccountCredentialsProvider) {
      getCredentials('target') >> target
    }
    def op = new AllowLaunchAtomicOperation(new AllowLaunchDescription(amiName: 'super-awesome-ami', account: 'target', credentials: source))
    op.accountCredentialsProvider = creds
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    ec2.describeTags(_) >> new DescribeTagsResult()
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
        assert dir.owners
        assert dir.owners.size() == 1
        assert dir.owners.first() == '67890'
        assert dir.filters
        assert dir.filters.size() == 1
        assert dir.filters.first().name == 'name'
        assert dir.filters.first().values == ['super-awesome-ami']

        new DescribeImagesResult().withImages(new Image().withImageId('ami-12345'))
    }
    1 * ec2.modifyImageAttribute(_)
  }

  void "image attribute modification is invoked on request"() {
    setup:
    def ec2 = Mock(AmazonEC2) {
      describeTags(_) >> new DescribeTagsResult()
      describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId('ami-123456'))
    }
    def provider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _, true) >> ec2
    }
    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: Stub(NetflixAmazonCredentials))
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
      def mock = Stub(NetflixAmazonCredentials)
      mock.getAccountId() >> 5678
      mock
    }
  }

  void "should replicate tags"() {
    def prodCredentials = TestCredential.named('prod')
    def testCredentials = TestCredential.named('test')

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
      1 * getAmazonEC2(testCredentials, _, true) >> sourceAmazonEc2
      1 * getAmazonEC2(prodCredentials, _, true) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-123456"))
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b:"1", c: "2"])
      1 * deleteTags(new DeleteTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b", value: "1"), new Tag(key: "c", value: "2")]))
      1 * createTags(new CreateTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b", value: "2")]))
    }
  }

  void "should skip tag replication when target account is the same as the requesting account"() {
    def testCredentials = TestCredential.named('test')

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
      1 * getAmazonEC2(testCredentials, _, true) >> sourceAmazonEc2
      1 * getAmazonEC2(testCredentials, _, true) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-123456"))
      1 * modifyImageAttribute(_)
    }

    0 * _
  }

  Closure<DescribeTagsResult> constructDescribeTagsResult = { Map tags ->
    new DescribeTagsResult(tags: tags.collect {new TagDescription(key: it.key, value: it.value) })
  }
}
