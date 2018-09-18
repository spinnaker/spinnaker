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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.model.DescribeImagesRequest
import com.amazonaws.services.ecr.model.DescribeImagesResult
import com.amazonaws.services.ecr.model.ImageDetail
import com.amazonaws.services.ecr.model.ImageIdentifier
import com.amazonaws.services.ecr.model.ListImagesResult
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.model.EcsDockerImage
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject

class EcrImageProviderSpec extends Specification {

  def amazonClientProvider = Mock(AmazonClientProvider)
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  @Subject
  def provider = new EcrImageProvider(amazonClientProvider, accountCredentialsProvider)

  def 'should the handle url'() {
    given:
    def url = '123456789012.dkr.ecr.us-west-1.amazonaws.com/test-repo:latest'

    when:
    boolean handles = provider.handles(url)

    then:
    handles
  }

  def 'should not the handle url'() {
    given:
    def url = '123456789012.dkr.rce.us-west-1.amazonaws.com/test-repo:latest'

    when:
    boolean handles = provider.handles(url)

    then:
    !handles
  }

  def 'should retrieve image details based on tagged url'() {
    given:
    def tag = 'latest'
    def region = 'us-west-1'
    def repoName = 'test-repo'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def creationDate = new Date()
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + ':' + tag

    def imageDetail = new ImageDetail(
      imageTags: [tag],
      repositoryName: repoName,
      registryId: accountId,
      imageDigest: digest,
      imagePushedAt: creationDate
    )

    def amazonECR = Mock(AmazonECR)

    amazonClientProvider.getAmazonEcr(_, _, _) >> amazonECR
    accountCredentialsProvider.getAll() >> [TestCredential.named('')]
    amazonECR.listImages(_) >> new ListImagesResult().withImageIds(Collections.emptyList())
    amazonECR.describeImages(_) >> new DescribeImagesResult().withImageDetails(imageDetail)

    def expectedListOfImages = [new EcsDockerImage(
      region: region,
      imageName: accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest,
      amis: ['us-west-1': Collections.singletonList(digest)],
      attributes: [creationDate: creationDate]
    )]

    when:
    def retrievedListOfImages = provider.findImage(url)

    then:
    retrievedListOfImages == expectedListOfImages
  }

  def 'should retrieve image details based on digest url'() {
    given:
    def region = 'us-west-1'
    def repoName = 'test-repo'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def creationDate = new Date()
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest

    def imageDetail = new ImageDetail(
      imageTags: [],
      repositoryName: repoName,
      registryId: accountId,
      imageDigest: digest,
      imagePushedAt: creationDate
    )

    def amazonECR = Mock(AmazonECR)

    amazonClientProvider.getAmazonEcr(_, _, _) >> amazonECR
    accountCredentialsProvider.getAll() >> [TestCredential.named('')]
    amazonECR.listImages(_) >> new ListImagesResult().withImageIds(Collections.emptyList())
    amazonECR.describeImages(_) >> new DescribeImagesResult().withImageDetails(imageDetail)

    def expectedListOfImages = [new EcsDockerImage(
      region: region,
      imageName: accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest,
      amis: ['us-west-1': Collections.singletonList(digest)],
      attributes: [creationDate: creationDate]
    )]

    when:
    def retrievedListOfImages = provider.findImage(url)

    then:
    retrievedListOfImages == expectedListOfImages
  }

  def 'should throw exception due to malformed account'() {
    given:
    def region = 'us-west-1'
    def repoName = 'test-repo'
    def accountId = '1234567890'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest

    when:
    provider.findImage(url)

    then:
    final IllegalArgumentException error = thrown()
    error.message == "The repository URI provided does not contain a proper account ID."
  }

  def 'should throw exception due to missing repository name'() {
    given:
    def region = 'us-west-1'
    def repoName = ''
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest

    when:
    provider.findImage(url)

    then:
    final IllegalArgumentException error = thrown()
    error.message == "The repository URI provided does not contain a proper repository name."
  }

  def 'should throw exception due to malformed region'() {
    given:
    def region = 'us-1-west'
    def repoName = 'test-repo'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest

    when:
    provider.findImage(url)

    then:
    final IllegalArgumentException error = thrown()
    error.message == "The repository URI provided does not contain a proper region."
  }

  def 'should throw exception due to invalid region'() {
    given:
    def region = 'us-west-1337'
    def repoName = 'test-repo'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest

    accountCredentialsProvider.getAll() >> [TestCredential.named('')]

    when:
    provider.findImage(url)

    then:
    final IllegalArgumentException error = thrown()
    error.message == "The repository URI provided does not belong to a region that the credentials have access to or the region is not valid."
  }

  def 'should find the image in a repository with a large number of images'() {
    given:
    def tag = 'latest'
    def region = 'us-west-1'
    def repoName = 'too-many'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + ':' + tag
    def imageId = new ImageIdentifier().withImageTag(tag).withImageDigest(digest)
    def creationDate = new Date()

    def amazonECR = Mock(AmazonECR)

    amazonClientProvider.getAmazonEcr(_, _, _) >> amazonECR
    accountCredentialsProvider.getAll() >> [TestCredential.named('')]

    amazonECR.listImages(_) >>> [
      new ListImagesResult()
        .withImageIds(new ImageIdentifier().withImageTag("notlatest1").withImageDigest("sha256:aaa"))
        .withNextToken("next1"),
      new ListImagesResult()
        .withImageIds(new ImageIdentifier().withImageTag("notlatest2").withImageDigest("sha256:bbb"))
        .withImageIds(imageId)
        .withNextToken(null),
    ]
    amazonECR.describeImages(
      new DescribeImagesRequest()
        .withRegistryId(accountId)
        .withRepositoryName(repoName)
        .withImageIds(imageId)
    ) >> new DescribeImagesResult()
      .withImageDetails(new ImageDetail(
        imageTags: [tag],
        repositoryName: repoName,
        registryId: accountId,
        imageDigest: digest,
        imagePushedAt: creationDate,
      ))

    def expectedImages = [new EcsDockerImage(
      region: region,
      imageName: accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest,
      amis: ['us-west-1': Collections.singletonList(digest)],
      attributes: [creationDate: creationDate],
    )]

    when:
    def retrievedListOfImages = provider.findImage(url)

    then:
    retrievedListOfImages == expectedImages
  }
}
