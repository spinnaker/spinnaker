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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.Image
import com.netflix.spinnaker.oort.data.aws.cachers.ImageCachingAgent
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount

class ImageCachingAgentSpec extends AbstractCachingAgentSpec {

  @Override
  ImageCachingAgent getCachingAgent() {
    new ImageCachingAgent(Mock(AmazonNamedAccount), REGION)
  }

  void "load new images and remove images that have disappeared since the last run"() {
    setup:
    def image1 = new Image().withImageId("ami-12345")
    def image2 = new Image().withImageId("ami-67890")
    def result = new DescribeImagesResult().withImages([image1, image2])

    when:
    "the brand new images show up, they should be fired as newImage"
    agent.load()

    then:
    1 * amazonEC2.describeImages() >> result
    1 * cacheService.put(Keys.getImageKey(image1.imageId, REGION), image1)
    1 * cacheService.put(Keys.getImageKey(image2.imageId, REGION), image2)

    when:
    "one of them is deleted, it is cleared from the cache"
    agent.load()

    then:
    1 * amazonEC2.describeImages() >> result.withImages([image2])
    0 * cacheService.put(_, _)
    1 * cacheService.free(Keys.getImageKey(image1.imageId, REGION))

    when:
    "the same results come back, it shouldnt do anything"
    agent.load()

    then:
    1 * amazonEC2.describeImages() >> result
    0 * cacheService.put(_, _)
  }

  void "new images should be stored in cache"() {
    setup:
    def imageId = "ami-12345"
    def image = new Image().withImageId(imageId)

    when:
    ((ImageCachingAgent)agent).loadNewImage(image, REGION)

    then:
    1 * cacheService.put(Keys.getImageKey(imageId, REGION), image)
  }

  void "removed image should be freed from cache"() {
    setup:
    def imageId = "ami-12345"

    when:
    ((ImageCachingAgent)agent).removeImage(imageId, REGION)

    then:
    1 * cacheService.free(Keys.getImageKey(imageId, REGION))
  }
}
