/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.controllers

import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.netflix.spinnaker.oort.gce.model.callbacks.ImagesCallback
import spock.lang.Specification

class GoogleNamedImageLookupControllerSpec extends Specification {
  void "public image lists are pruned"() {
    setup:
      def imageList = new ArrayList<String>()
      def imagesCallback1 = new ImagesCallback(imageList, true)
      def imageListResult1 = new ImageList()
      imageListResult1.setItems(buildImageList(["backports-debian-7-wheezy-v20141017",
                                                "backports-debian-7-wheezy-v20141021",
                                                "backports-debian-7-wheezy-v20141108",
                                                "debian-7-wheezy-v20141017",
                                                "debian-7-wheezy-v20141021",
                                                "debian-7-wheezy-v20141108"]))

      def imagesCallback2 = new ImagesCallback(imageList, true)
      def imageListResult2 = new ImageList()
      imageListResult2.setItems(buildImageList(["ubuntu-1404-trusty-v20141028",
                                                "ubuntu-1404-trusty-v20141031a"]))

    when:
      imagesCallback1.onSuccess(imageListResult1, null)
      imagesCallback2.onSuccess(imageListResult2, null)

    then:
      imageList == buildImageList(["backports-debian-7-wheezy-v20141108",
                                   "debian-7-wheezy-v20141108",
                                   "ubuntu-1404-trusty-v20141031a"])
  }

  void "project image lists are not pruned"() {
    setup:
      def imageList = new ArrayList<String>()
      def imagesCallback = new ImagesCallback(imageList, false)
      def imageListResult = new ImageList()
      imageListResult.setItems(buildImageList(["my-image-1",
                                               "my-image-2",
                                               "my-image-3",
                                               "my-other-image-1",
                                               "my-other-image-2",
                                               "my-other-image-3",]))

    when:
      imagesCallback.onSuccess(imageListResult, null)

    then:
      imageList == buildImageList(["my-image-1", "my-image-2", "my-image-3",
                                   "my-other-image-1", "my-other-image-2", "my-other-image-3"])
  }

  private List<Image> buildImageList(List<String> imageNameList) {
    imageNameList.collect { imageName ->
      new Image(name: imageName)
    }
  }
}
