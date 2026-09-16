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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.*
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Specification

class AllowLaunchAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "image amiId is resolved from name"() {
    setup:
    def ec2 = Mock(Ec2Client)
    def provider = Stub(AmazonClientProvider) {
      getAmazonEC2V2(_, _) >> ec2
    }

    def target = Stub(NetflixAmazonCredentials) {
      getAccountId() >> '12345'
    }

    def source = Stub(NetflixAmazonCredentials) {
      getAccountId() >> '67890'
    }

    def creds = Stub(CredentialsRepository) {
      getOne('target') >> target
    }
    def op = new AllowLaunchAtomicOperation(new AllowLaunchDescription(amiName: 'super-awesome-ami', targetAccount: 'target', credentials: source))
    op.credentialsRepository = creds
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    ec2.describeTags(_) >> DescribeTagsResponse.builder().build()
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
        assert dir.executableUsers
        assert dir.executableUsers.size() == 1
        assert dir.executableUsers.first() == '12345'
        assert dir.filters
        assert dir.filters.size() == 1
        assert dir.filters.first().name == 'name'
        assert dir.filters.first().values == ['super-awesome-ami']

        DescribeImagesResponse.builder().images(Image.builder().imageId('ami-12345').ownerId('67890').build()).build()
    }
  }

  void "image attribute modification is invoked on request"() {
    setup:
    def prodCredentials = TestCredential.named('prod')
    def testCredentials = TestCredential.named('test')

    def sourceAmazonEc2 = Mock(Ec2Client) {
      describeTags(_) >> DescribeTagsResponse.builder().build()
      describeImages(_) >> DescribeImagesResponse.builder().images(Image.builder().imageId('ami-123456').ownerId(testCredentials.accountId).build()).build()
    }
    def targetAmazonEc2 = Mock(Ec2Client) {
      describeTags(_) >> DescribeTagsResponse.builder().build()
      describeImages(_) >> null
    }
    def provider = Mock(AmazonClientProvider)
    def description = new AllowLaunchDescription(targetAccount: "prod", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository){
      1 * getOne("prod") >> prodCredentials
    }
    with(provider) {
      1 * getAmazonEC2V2(testCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2V2(prodCredentials, _) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * modifyImageAttribute(_) >> { ModifyImageAttributeRequest request ->
        assert request.launchPermission.add.get(0).userId == prodCredentials.accountId
      }
    }
  }

  void "should replicate tags"() {
    def prodCredentials = TestCredential.named('prod')
    def testCredentials = TestCredential.named('test')

    def sourceAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)
    def provider = Mock(AmazonClientProvider)

    def description = new AllowLaunchDescription(targetAccount: "prod", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository){
      1 * getOne("prod") >> prodCredentials
    }
    with(provider) {
      1 * getAmazonEC2V2(testCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2V2(prodCredentials, _) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(Image.builder().imageId("ami-123456").ownerId(testCredentials.accountId).build()).build()
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b:"1", c: "2"])
      1 * deleteTags(DeleteTagsRequest.builder().resources(["ami-123456"]).tags([Tag.builder().key("b").value("1").build(), Tag.builder().key("c").value("2").build()]).build())
      1 * createTags(CreateTagsRequest.builder().resources(["ami-123456"]).tags([Tag.builder().key("b").value("2").build()]).build())
    }
  }

  void "should skip tag replication when target account is the same as the requesting account"() {
    def testCredentials = TestCredential.named('test')

    def sourceAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)
    def provider = Mock(AmazonClientProvider)

    def description = new AllowLaunchDescription(targetAccount: "test", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository){
      1 * getOne("test") >> testCredentials
    }
    with(provider) {
      1 * getAmazonEC2V2(testCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2V2(testCredentials, _) >> targetAmazonEc2
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(Image.builder().imageId("ami-123456").ownerId(testCredentials.accountId).build()).build()
    }

    0 * _
  }

  void "should lookup owner account of resolved ami"() {
    setup:
    def ownerCredentials = TestCredential.named('owner')
    def sourceCredentials = TestCredential.named('source')
    def targetCredentials = TestCredential.named('target')

    def ownerAmazonEc2 = Mock(Ec2Client)
    def sourceAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)

    def description = new AllowLaunchDescription(targetAccount: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: sourceCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository) {
      1 * getOne('target') >> targetCredentials
      1 * getAll() >> [sourceCredentials, targetCredentials, ownerCredentials]
    }

    with(op.amazonClientProvider) {
      1 * getAmazonEC2V2(sourceCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2V2(targetCredentials, _) >> targetAmazonEc2
      1 * getAmazonEC2V2(ownerCredentials, _) >> ownerAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(Image.builder().imageId("ami-123456").ownerId(ownerCredentials.accountId).build()).build()
    }
    with(ownerAmazonEc2) {
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b:"1", c: "2"])
      1 * deleteTags(DeleteTagsRequest.builder().resources(["ami-123456"]).tags([Tag.builder().key("b").value("1").build(), Tag.builder().key("c").value("2").build()]).build())
      1 * createTags(CreateTagsRequest.builder().resources(["ami-123456"]).tags([Tag.builder().key("b").value("2").build()]).build())
    }

  }

  void "should return resolved public AMI without performing additional operations"() {
    setup:
    def ownerCredentials = TestCredential.named('owner')
    def targetCredentials = TestCredential.named('target')
    def ownerAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)

    def description = new AllowLaunchDescription(targetAccount: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: ownerCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:

    with(op.credentialsRepository) {
      1 * getOne('target') >> targetCredentials
    }

    with(op.amazonClientProvider) {
      1 * getAmazonEC2V2(targetCredentials, _) >> targetAmazonEc2
      1 * getAmazonEC2V2(ownerCredentials, _) >> ownerAmazonEc2
    }

    with(targetAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(
        Image.builder().imageId("ami-123456").ownerId(ownerCredentials.accountId).publicLaunchPermissions(true).build()).build()
    }
    0 * _

  }

  void 'should return resolved third-party private AMI when account allows'() {
    setup:
    def sourceCredentials = TestCredential.named('source')
    def targetCredentials = TestCredential.named('target', [allowPrivateThirdPartyImages: true])
    def sourceAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)

    def description = new AllowLaunchDescription(targetAccount: 'target', amiName: 'ami-123456', region: 'us-west-2', credentials: sourceCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository) {
      1 * getOne('target') >> targetCredentials
    }
    with(op.amazonClientProvider) {
      1 * getAmazonEC2V2(targetCredentials, _) >> targetAmazonEc2
      1 * getAmazonEC2V2(sourceCredentials, _) >> sourceAmazonEc2
    }
    with(targetAmazonEc2) {
      3 * describeImages(_) >> DescribeImagesResponse.builder().build()
    }
    with(sourceAmazonEc2) {
      3 * describeImages(_) >> DescribeImagesResponse.builder().build()
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(
        Image.builder().imageId('ami-123456').ownerId('thirdparty').build()
      ).build()
    }
  }

  Closure<DescribeTagsResponse> constructDescribeTagsResult = { Map tags ->
    DescribeTagsResponse.builder().tags(tags.collect {TagDescription.builder().key(it.key).value(it.value).build() }).build()
  }

  void "should skip allow launch on resolved target AMI if found but still perform tag syncing"() {
    setup:
    def ownerCredentials = TestCredential.named('owner')
    def targetCredentials = TestCredential.named('target')
    def ownerAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)

    def description = new AllowLaunchDescription(targetAccount: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: ownerCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository) {
      1 * getOne('target') >> targetCredentials
    }

    with(op.amazonClientProvider) {
      1 * getAmazonEC2V2(targetCredentials, _) >> targetAmazonEc2
      1 * getAmazonEC2V2(ownerCredentials, _) >> ownerAmazonEc2
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(
        Image.builder().imageId("ami-123456").ownerId(ownerCredentials.accountId).build()).build()
      1 * describeTags(_) >> DescribeTagsResponse.builder().tags(TagDescription.builder().key("existingTag").value("existingValue").build()).build()
      1 * createTags(_) >> { CreateTagsRequest ctr ->
        assert ctr.tags().size() == 1
        assert ctr.tags().get(0).key() == "missingTag"
        assert ctr.tags().get(0).value() == "missingValue"
      }
    }
    with (ownerAmazonEc2) {
      1 * describeTags(_) >> DescribeTagsResponse.builder().tags(TagDescription.builder().key("existingTag").value("existingValue").build(), TagDescription.builder().key("missingTag").value("missingValue").build()).build()
    }
    0 * _

  }

  void "should skip allow launch and tag syncing on resolved target AMI if owner account cannot be resolved"() {
    setup:
    def sourceCredentials = TestCredential.named('source')
    def targetCredentials = TestCredential.named('target')
    def sourceAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)
    def description = new AllowLaunchDescription(targetAccount: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: sourceCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    with(op.credentialsRepository) {
      1 * getOne('target') >> targetCredentials
      1 * getAll() >> [sourceCredentials, targetCredentials]
    }
    with(op.amazonClientProvider) {
      1 * getAmazonEC2V2(sourceCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2V2(targetCredentials, _) >> targetAmazonEc2
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(
        Image.builder().imageId("ami-123456").ownerId('unknown').build()
      ).build()
    }
    0 * _
  }

  void "should throw exception when AMI is resolved in source but the owner account cannot be resolved"() {
    setup:
    def sourceCredentials = TestCredential.named('source')
    def targetCredentials = TestCredential.named('target')
    def sourceAmazonEc2 = Mock(Ec2Client)
    def targetAmazonEc2 = Mock(Ec2Client)
    def description = new AllowLaunchDescription(targetAccount: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: sourceCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.credentialsRepository = Mock(CredentialsRepository)

    when:
    op.operate([])

    then:
    thrown IllegalArgumentException
    with(op.credentialsRepository) {
      1 * getOne('target') >> targetCredentials
      1 * getAll() >> [sourceCredentials, targetCredentials]
    }
    with(op.amazonClientProvider) {
      1 * getAmazonEC2V2(sourceCredentials, _) >> sourceAmazonEc2
      1 * getAmazonEC2V2(targetCredentials, _) >> targetAmazonEc2
    }
    with(targetAmazonEc2) {
      3 * describeImages(_) >> DescribeImagesResponse.builder().build()
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> DescribeImagesResponse.builder().images(
        Image.builder().imageId("ami-123456").ownerId('unknown').build()
      ).build()
    }
    0 * _
  }
}
