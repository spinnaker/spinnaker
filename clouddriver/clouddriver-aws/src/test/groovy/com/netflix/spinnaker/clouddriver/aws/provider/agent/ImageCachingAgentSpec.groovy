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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Image
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
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
  AmazonEC2 ec2

  def setup() {
    ec2 = Mock(AmazonEC2)
    publicImage = new Image().withImageId('ami-11111111').withName('public').withPublic(true)
    privateImage = new Image().withImageId('ami-22222222').withName('private').withPublic(false)
    privateImageKey = Keys.getImageKey(privateImage.getImageId(), accountName, region)
    publicImageKey = Keys.getImageKey(publicImage.getImageId(), accountName, region)
    privateNamedImageKey =  Keys.getNamedImageKey(accountName, privateImage.getName())
    publicNamedImageKey = Keys.getNamedImageKey(accountName, publicImage.getName())
  }

  def getAgent(boolean publicImages, boolean eddaEnabled) {
    getAgent(publicImages, eddaEnabled, List.of())
  }

  def getAgent(boolean publicImages, boolean eddaEnabled, List<String> imageStates) {
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> accountName
      it.getAccountId() >> accountId
      isEddaEnabled() >> eddaEnabled
    }
    def dcs = Stub(DynamicConfigService) {
      isEnabled(_ as String, true) >> true
      getConfig(List, "aws.defaults.image-states", List.of()) >> imageStates
    }
    def acp = Stub(AmazonClientProvider) {
      getAmazonEC2(creds, region, _) >> ec2
    }
    new ImageCachingAgent(acp, creds, region, AmazonObjectMapperConfigurer.createConfigured(), Spectator.globalRegistry(), publicImages, dcs)
  }

  void "two images with the same name result in one named image"() {
    given: 'two images with the same name'
    // amis have unique ids, but it's possible for two amis with the same name
    // to exist in the same account (and potentially the same region).
    String imageName = 'foo'
    Image imageOne = new Image().withImageId('ami-1').withName(imageName)
    Image imageTwo = new Image().withImageId('ami-2').withName(imageName)
    String imageOneKey = Keys.getNamedImageKey(accountName, imageOne.getName())
    String imageTwoKey = Keys.getNamedImageKey(accountName, imageTwo.getName())

    and:
    // arbitrary values for publicImages and eddaEnabled, but the expected
    // request corresponds to them
    def agent = getAgent(false, false)
    def request = new DescribeImagesRequest().withFilters(new Filter('is-public', ['false']))

    when:
    def result = agent.loadData(providerCache)

    then: 'the result has one named image'
    1 * ec2.describeImages(request) >> new DescribeImagesResult(images: [imageOne, imageTwo])
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
    // arbitrary values for publicImages and eddaEnabled, but the expected
    // request corresponds to them
    def imageStates = ['available', 'failed']
    def agent = getAgent(false, false, imageStates)
    def request = new DescribeImagesRequest().withFilters(new Filter('is-public', ['false']), new Filter('state', imageStates))

    when:
    def result = agent.loadData(providerCache)

    then:
    // arbitrarily choose the image to return
    1 * ec2.describeImages(request) >> new DescribeImagesResult(images: [privateImage])
    0 * _
  }

  void "should include only private images"() {
    given:
    def agent = getAgent(false, false)
    def request = new DescribeImagesRequest().withFilters(new Filter('is-public', ['false']))

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeImages(request) >> new DescribeImagesResult(images: [privateImage])
    0 * _

    result.cacheResults[IMAGES.ns].find { it.id == privateImageKey }
    result.cacheResults[NAMED_IMAGES.ns].find { it.id == privateNamedImageKey }
    !result.cacheResults[IMAGES.ns].find { it.id == publicImageKey }
    !result.cacheResults[NAMED_IMAGES.ns].find { it.id == publicNamedImageKey }
  }

  void "should include only public images"() {
    given:
    def agent = getAgent(true, false)
    def request = new DescribeImagesRequest().withFilters(new Filter('is-public', ['true']))

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeImages(request) >> new DescribeImagesResult(images: [publicImage])
    0 * _

    result.cacheResults[IMAGES.ns].find { it.id == publicImageKey }
    result.cacheResults[NAMED_IMAGES.ns].find { it.id == publicNamedImageKey }
    !result.cacheResults[IMAGES.ns].find { it.id == privateImageKey }
    !result.cacheResults[NAMED_IMAGES.ns].find { it.id == privateNamedImageKey }
  }

  void "should manually filter private images from Edda"() {
    given:
    def agent = getAgent(false, true)
    def request = new DescribeImagesRequest().withFilters(new Filter('is-public', ['false']))

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeImages(request) >> new DescribeImagesResult(images: [privateImage, publicImage])
    0 * _

    result.cacheResults[IMAGES.ns].find { it.id == privateImageKey }
    result.cacheResults[NAMED_IMAGES.ns].find { it.id == privateNamedImageKey }
    !result.cacheResults[IMAGES.ns].find { it.id == publicImageKey }
    !result.cacheResults[NAMED_IMAGES.ns].find { it.id == publicNamedImageKey }
  }

  void "should manually filter public images from Edda"() {
    given:
    def agent = getAgent(true, true)
    def request = new DescribeImagesRequest().withFilters(new Filter('is-public', ['true']))

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * ec2.describeImages(request) >> new DescribeImagesResult(images: [privateImage, publicImage])
    0 * _

    result.cacheResults[IMAGES.ns].find { it.id == publicImageKey }
    result.cacheResults[NAMED_IMAGES.ns].find { it.id == publicNamedImageKey }
    !result.cacheResults[IMAGES.ns].find { it.id == privateImageKey }
    !result.cacheResults[NAMED_IMAGES.ns].find { it.id == privateNamedImageKey }
  }

}
