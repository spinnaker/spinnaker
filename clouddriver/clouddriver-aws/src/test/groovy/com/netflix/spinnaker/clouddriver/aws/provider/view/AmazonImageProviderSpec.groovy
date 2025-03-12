/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.amazonaws.services.ec2.model.Image
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.model.AmazonImage
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import com.netflix.spinnaker.clouddriver.aws.data.Keys

class AmazonImageProviderSpec extends Specification {
  Cache cache = Mock(Cache)
  AwsConfiguration.AmazonServerGroupProvider amazonServerGroupProvider = Mock(AwsConfiguration.AmazonServerGroupProvider)
  ObjectMapper objectMapper = new ObjectMapper()

  @Subject
  AmazonImageProvider provider = new AmazonImageProvider(cache, amazonServerGroupProvider, objectMapper)

  void "should return one image"() {
    when:
    def result = provider.getImageById("ami-123321")

    then:
    AmazonImage expectedImage = new AmazonImage()
    expectedImage.setRegion("eu-west-1")
    expectedImage.setImage(new Image())
    expectedImage.image.setImageId("ami-123321")
    expectedImage.image.setName("some_ami")
    expectedImage.image.setOwnerId("1233211233231")
    result == Optional.of(expectedImage)

    and:
    1 * cache.filterIdentifiers(IMAGES.ns, _ as String) >> [
        "aws:images:test_account:eu-west-1:ami-123321"
    ]

    1 * cache.getAll(IMAGES.ns, ["aws:images:test_account:eu-west-1:ami-123321"]) >>
        [imageCacheData('ami-123321', [
            ownerId: '1233211233231',
            name   : 'some_ami',
            account   : 'test_account',
            region   : 'eu-west-1',
            imageId: 'ami-123321'])]
  }

  void "should not find any image"() {
    when:
    def result = provider.getImageById("ami-123321")

    then:
    result == Optional.empty()

    and:
    1 * cache.filterIdentifiers(IMAGES.ns, _ as String) >> []
  }

  void "should throw exception of invalid ami name"() {
    when:
    provider.getImageById("amiz-123321")

    then:
    thrown(RuntimeException)
  }

  static private CacheData imageCacheData(String imageId, Map attributes) {
    new DefaultCacheData(Keys.getImageKey(imageId, attributes.account, attributes.region), attributes, [:])
  }
}
