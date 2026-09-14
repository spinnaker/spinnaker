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

import software.amazon.awssdk.services.ecr.EcrClient
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest
import software.amazon.awssdk.services.ecr.model.DescribeImagesResponse
import software.amazon.awssdk.services.ecr.model.ImageDetail
import software.amazon.awssdk.services.ecr.model.ImageIdentifier
import software.amazon.awssdk.services.ecr.model.ListImagesResponse
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.model.EcsDockerImage
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import spock.lang.Specification
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Subject

class EcrImageProviderSpec extends Specification {

  def amazonClientProvider = Mock(AmazonClientProvider)
  def credentialsRepository = Mock(CredentialsRepository)
  @Subject
  def provider = new EcrImageProvider(amazonClientProvider, credentialsRepository)

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
    def creationDate = java.time.Instant.now()
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + ':' + tag

    def imageDetail = ImageDetail.builder()
      .imageTags([tag])
      .repositoryName(repoName)
      .registryId(accountId)
      .imageDigest(digest)
      .imagePushedAt(creationDate)
      .build()

    def amazonECR = Mock(EcrClient)

    amazonClientProvider.getAmazonEcrV2(_, _) >> amazonECR
    credentialsRepository.getAll() >> [new NetflixECSCredentials(TestCredential.named('')) ]
    amazonECR.listImages(_) >> ListImagesResponse.builder().imageIds(Collections.emptyList()).build()
    amazonECR.describeImages(_) >> DescribeImagesResponse.builder().imageDetails(imageDetail).build()

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
    def creationDate = java.time.Instant.now()
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest

    def imageDetail = ImageDetail.builder()
      .imageTags([])
      .repositoryName(repoName)
      .registryId(accountId)
      .imageDigest(digest)
      .imagePushedAt(creationDate)
      .build()

    def amazonECR = Mock(EcrClient)

    amazonClientProvider.getAmazonEcrV2(_, _) >> amazonECR
    credentialsRepository.getAll() >> [new NetflixECSCredentials(TestCredential.named('')) ]
    amazonECR.listImages(_) >> ListImagesResponse.builder().imageIds(Collections.emptyList()).build()
    amazonECR.describeImages(_) >> DescribeImagesResponse.builder().imageDetails(imageDetail).build()

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

  def 'should find second credential when two share account ids'() {
    given:
    def region = 'us-east-1'
    def repoName = 'repositoryname'
    def accountId = '123456789012'
    def tag = 'arbitrary-tag'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def creationDate = java.time.Instant.now()
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + ':' + tag// + '@' + digest
    def imageDetail = ImageDetail.builder()
      .repositoryName(repoName)
      .registryId(accountId)
      .imageDigest(digest)
      .imageTags(List.of(tag))
      .imagePushedAt(creationDate)
      .build()

    Map<String, Object> region1 = Map.of(
      'name', 'eu-west-1',
      'availabilityZones', Arrays.asList('eu-west-1a', 'eu-west-1b', 'eu-west-1c')
    )
    Map<String, Object> overrides1 = Map.of(
      'accountId', accountId,
      'regions', Arrays.asList(region1)
    )

    Map<String, Object> region2 = Map.of(
      'name', region,
      'availabilityZones', Arrays.asList('us-east-1a', 'us-east-1b', 'us-east-1c')
    )
    Map<String, Object> overrides2 = Map.of(
      'accountId', accountId,
      'regions', Arrays.asList(region2)
    )

    credentialsRepository.getAll() >> [
      new NetflixECSCredentials(TestCredential.named('incorrect-region', overrides1)),
      new NetflixECSCredentials(TestCredential.named('correct-region', overrides2))]

    def amazonECR = Mock(EcrClient)

    amazonClientProvider.getAmazonEcrV2(_, _) >> amazonECR
    amazonECR.listImages(_) >> ListImagesResponse.builder().imageIds(Collections.emptyList()).build()
    amazonECR.describeImages(_) >> DescribeImagesResponse.builder().imageDetails(imageDetail).build()

    def expectedListOfImages = [new EcsDockerImage(
      region: region,
      imageName: accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + '@' + digest,
      amis: ['us-east-1': Collections.singletonList(digest)],
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

    credentialsRepository.getAll() >> [new NetflixECSCredentials(TestCredential.named('')) ]

    when:
    provider.findImage(url)

    then:
    final com.netflix.spinnaker.kork.web.exceptions.NotFoundException error = thrown()
    error.message == String.format("AWS account %s with region %s was not found.  Please specify a valid account name and region", accountId, region)
  }

  def 'should find the image in a repository with a large number of images'() {
    given:
    def tag = 'latest'
    def region = 'us-west-1'
    def repoName = 'too-many'
    def accountId = '123456789012'
    def digest = 'sha256:deadbeef785192c146085da66a4261e25e79a6210103433464eb7f79deadbeef'
    def url = accountId + '.dkr.ecr.' + region + '.amazonaws.com/' + repoName + ':' + tag
    def imageId = ImageIdentifier.builder().imageTag(tag).imageDigest(digest).build()
    def creationDate = java.time.Instant.now()

    def amazonECR = Mock(EcrClient)

    amazonClientProvider.getAmazonEcrV2(_, _) >> amazonECR
    credentialsRepository.getAll() >> [new NetflixECSCredentials(TestCredential.named('')) ]

    amazonECR.listImages(_) >>> [
      ListImagesResponse.builder()
        .imageIds(ImageIdentifier.builder().imageTag("notlatest1").imageDigest("sha256:aaa").build())
        .nextToken("next1")
        .build(),
      ListImagesResponse.builder()
        .imageIds(ImageIdentifier.builder().imageTag("notlatest2").imageDigest("sha256:bbb").build(), imageId)
        .nextToken(null)
        .build(),
    ]
    amazonECR.describeImages(
      DescribeImagesRequest.builder()
        .registryId(accountId)
        .repositoryName(repoName)
        .imageIds(imageId)
        .build()
    ) >> DescribeImagesResponse.builder()
      .imageDetails(ImageDetail.builder()
        .imageTags([tag])
        .repositoryName(repoName)
        .registryId(accountId)
        .imageDigest(digest)
        .imagePushedAt(creationDate)
        .build())
      .build()

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
