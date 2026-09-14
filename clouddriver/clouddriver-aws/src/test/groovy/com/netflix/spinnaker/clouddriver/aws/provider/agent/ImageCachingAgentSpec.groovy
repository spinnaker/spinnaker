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

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.Image
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES

class ImageCachingAgentSpec extends Specification {
  static String region = 'region'
  static String accountName = 'accountName'
  static String accountId = 'accountId'

  @Shared
  Image publicImage

  @Shared
  Image privateImage

  @Shared
  String privateImageKey

  @Shared
  String publicImageKey

  @Shared
  String privateNamedImageKey

  @Shared
  String publicNamedImageKey

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Shared
  NetflixAmazonCredentials creds

  @Shared
  Ec2Client ec2

  def setup() {
    ec2 = Mock(Ec2Client)
    publicImage = Image.builder().imageId('ami-11111111').name('public').publicLaunchPermissions(true).build()
    privateImage = Image.builder().imageId('ami-22222222').name('private').publicLaunchPermissions(false).build()
    privateImageKey = Keys.getImageKey(privateImage.imageId(), accountName, region)
    publicImageKey = Keys.getImageKey(publicImage.imageId(), accountName, region)
    privateNamedImageKey =  Keys.getNamedImageKey(accountName, privateImage.name())
    publicNamedImageKey = Keys.getNamedImageKey(accountName, publicImage.name())
  }

  def getAgent(boolean publicImages) {
    getAgent(publicImages, List.of())
  }

  def getAgent(boolean publicImages, List<String> imageStates) {
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> accountName
      it.getAccountId() >> accountId
    }
    def dcs = Stub(DynamicConfigService) {
      isEnabled(_ as String, true) >> true
      getConfig(List, "aws.defaults.image-states", List.of()) >> imageStates
    }
    def acp = Stub(AmazonClientProvider) {
      getAmazonEC2V2(creds, region) >> ec2
    }
    new ImageCachingAgent(acp, creds, region, AmazonObjectMapperConfigurer.createConfigured().registerModule(new AwsSdkV2Module()), Spectator.globalRegistry(), publicImages, dcs)
  }

  void "two images with the same name result in one named image"() {
    given: 'two images with the same name'
    // amis have unique ids, but it's possible for two amis with the same name
    // to exist in the same account (and potentially the same region).
    String imageName = 'foo'
    Image imageOne = Image.builder().imageId('ami-1').name(imageName).build()
    Image imageTwo = Image.builder().imageId('ami-2').name(imageName).build()

    and:
    def agent = getAgent(false)
    def request = DescribeImagesRequest.builder().filters(Filter.builder().name('is-public').values(['false']).build()).build()

    when:
    def result = agent.loadData(providerCache)

    then: 'the result has one named image'
    1 * ec2.describeImages(request) >> DescribeImagesResponse.builder().images(imageOne, imageTwo).build()
    0 * _

    result.cacheResults[NAMED_IMAGES.ns].size() == 1

    and: 'the named image is related to both amis'
    def imageRelationships = result.cacheResults[NAMED_IMAGES.ns][0].relationships[IMAGES.ns]
    imageRelationships.size() == 2
    imageRelationships.containsAll(Keys.getImageKey('ami-1', accountName, region),
                                   Keys.getImageKey('ami-2', accountName, region))
  }

  void "include the filter corresponding to the configured image states"() {
    given:
    def imageStates = ['available', 'failed']
    def agent = getAgent(false, imageStates)
    def request = DescribeImagesRequest.builder().filters(
      Filter.builder().name('is-public').values(['false']).build(),
      Filter.builder().name('state').values(imageStates).build()
    ).build()

    when:
    def result = agent.loadData(providerCache)

    then:
    // arbitrarily choose the image to return
    1 * ec2.describeImages(request) >> DescribeImagesResponse.builder().images(privateImage).build()
    0 * _
  }

  void "should include only private images"() {
    given:
    def agent = getAgent(false)
    def request = DescribeImagesRequest.builder().filters(Filter.builder().name('is-public').values(['false']).build()).build()

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeImages(request) >> DescribeImagesResponse.builder().images(privateImage).build()
    0 * _

    result.cacheResults[IMAGES.ns].find { it.id == privateImageKey }
    result.cacheResults[NAMED_IMAGES.ns].find { it.id == privateNamedImageKey }
    !result.cacheResults[IMAGES.ns].find { it.id == publicImageKey }
    !result.cacheResults[NAMED_IMAGES.ns].find { it.id == publicNamedImageKey }
  }

  void "should include only public images"() {
    given:
    def agent = getAgent(true)
    def request = DescribeImagesRequest.builder().filters(Filter.builder().name('is-public').values(['true']).build()).build()

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeImages(request) >> DescribeImagesResponse.builder().images(publicImage).build()
    0 * _

    result.cacheResults[IMAGES.ns].find { it.id == publicImageKey }
    result.cacheResults[NAMED_IMAGES.ns].find { it.id == publicNamedImageKey }
    !result.cacheResults[IMAGES.ns].find { it.id == privateImageKey }
    !result.cacheResults[NAMED_IMAGES.ns].find { it.id == privateNamedImageKey }
  }

}
