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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.ec2.model.Image
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract

@CompileStatic
class ImageCachingAgent extends AbstractInfrastructureCachingAgent {
  ImageCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownImages = [:]

  void load() {
    log.info "$cachePrefix - Beginning Image Cache Load."
    def amazonEC2 = amazonClientProvider.getAmazonEC2(account.credentials, region)

    def images = amazonEC2.describeImages()
    def allImages = images.images.collectEntries { Image image -> [(image.imageId): image] }
    Map<String, Integer> imagesThisRun = (Map<String, Integer>)allImages.collectEntries { imageId, image -> [(imageId): image.hashCode()] }
    Map<String, Integer> newImages =  specialSubtract(imagesThisRun, lastKnownImages)
    Set<String> missingImages = new HashSet<String>(lastKnownImages.keySet())
    missingImages.removeAll(imagesThisRun.keySet())

    if (newImages) {
      log.info "$cachePrefix - Loading ${newImages.size()} new images."
      for (imageId in newImages.keySet()) {
        Image image = (Image)allImages[imageId]
        loadNewImage(image, region)
      }
    }
    if (missingImages) {
      log.info "$cachePrefix - Removing ${missingImages.size()} missing images."
      for (imageId in missingImages) {
        removeImage(imageId, region)
      }
    }
    if (!newImages && !missingImages) {
      log.info "$cachePrefix - Nothing new to process"
    }

    lastKnownImages = imagesThisRun
  }

  void loadNewImage(Image image, String region) {
    cacheService.put(Keys.getImageKey(image.imageId, region), image)
  }

  void removeImage(String imageId, String region) {
    cacheService.free(Keys.getImageKey(imageId, region))
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:img]"
  }
}
