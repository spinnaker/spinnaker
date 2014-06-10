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
import reactor.event.Event

class ImageCachingAgentSpec extends AbstractCachingAgentSpec {

  @Override
  ImageCachingAgent getCachingAgent() {
    new ImageCachingAgent(Mock(AmazonNamedAccount), "us-east-1")
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
    2 * reactor.notify("newImage", _)

    when:
    "one of them is deleted, it should fire missingImage"
    agent.load()

    then:
    1 * amazonEC2.describeImages() >> result.withImages([image2])
    0 * reactor.notify("newImage", _)
    1 * reactor.notify("missingImage", _) >> { eventname, Event<String> event ->
      assert event.data == "ami-12345"
    }

    when:
    "the same results come back, it shouldnt do anything"
    agent.load()

    then:
    1 * amazonEC2.describeImages() >> result
    0 * reactor.notify(_, _)
  }

  void "new images should be stored in cache"() {
    setup:
    def imageId = "ami-12345"
    def region = "us-east-1"
    def image = new Image().withImageId(imageId)
    def event = Event.wrap(new ImageCachingAgent.NewImageNotification(image, region))

    when:
    ((ImageCachingAgent)agent).loadNewImage(event)

    then:
    1 * cacheService.put(Keys.getImageKey(imageId, region), image)
  }
}
