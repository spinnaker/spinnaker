/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model.callbacks

import com.google.api.services.compute.model.DeprecationStatus
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import spock.lang.Specification

class ImagesCallbackSpec extends Specification {
  void "public image lists are pruned"() {
    setup:
      def imageList = new ArrayList<String>()
      def imagesCallback1 = new ImagesCallback(imageList, true)
      def imageListResult1 = new ImageList()
      imageListResult1.setItems([buildImage("backports-debian-7-wheezy-v20141017", false),
                                 buildImage("backports-debian-7-wheezy-v20141021", false),
                                 buildImage("backports-debian-7-wheezy-v20141108", false),
                                 buildImage("debian-7-wheezy-v20141017", false),
                                 buildImage("debian-7-wheezy-v20141021", false),
                                 buildImage("debian-7-wheezy-v20141108", false),
                                 buildImage("someos-8-something-v20141021", false),
                                 buildImage("someos-8-something-v20141108", true),
                                 buildImage("otheros-9-something-v20141021", true),
                                 buildImage("otheros-9-something-v20141108", true)])

      def imagesCallback2 = new ImagesCallback(imageList, true)
      def imageListResult2 = new ImageList()
      imageListResult2.setItems([buildImage("ubuntu-1404-trusty-v20141028", false),
                                 buildImage("ubuntu-1404-trusty-v20141029", true),
                                 buildImage("ubuntu-1404-trusty-v20141031a", false)])

    when:
      imagesCallback1.onSuccess(imageListResult1, null)
      imagesCallback2.onSuccess(imageListResult2, null)

    then:
      imageList == [buildImage("backports-debian-7-wheezy-v20141108", false),
                    buildImage("debian-7-wheezy-v20141108", false),
                    buildImage("someos-8-something-v20141021", false),
                    buildImage("ubuntu-1404-trusty-v20141031a", false)]
  }

  void "public images with no deprecated struct are not filtered out"() {
    setup:
      def imageList = new ArrayList<String>()
      def imagesCallback1 = new ImagesCallback(imageList, true)
      def imageListResult1 = new ImageList()
      imageListResult1.setItems([new Image(name: "backports-debian-7-wheezy-v20141108"),
                                 new Image(name: "debian-7-wheezy-v20141108"),
                                 new Image(name: "someos-8-something-v20141108"),
                                 new Image(name: "otheros-9-something-v20141108")])

      def imagesCallback2 = new ImagesCallback(imageList, true)
      def imageListResult2 = new ImageList()
      imageListResult2.setItems([new Image(name: "ubuntu-1404-trusty-v20141028"),
                                 buildImage("ubuntu-1404-trusty-v20141029", true),
                                 new Image(name: "ubuntu-1404-trusty-v20141031a")])

    when:
      imagesCallback1.onSuccess(imageListResult1, null)
      imagesCallback2.onSuccess(imageListResult2, null)

    then:
      imageList == [new Image(name: "backports-debian-7-wheezy-v20141108"),
                    new Image(name: "debian-7-wheezy-v20141108"),
                    new Image(name: "otheros-9-something-v20141108"),
                    new Image(name: "someos-8-something-v20141108"),
                    new Image(name: "ubuntu-1404-trusty-v20141031a")]
  }

  void "project image lists are not pruned"() {
    setup:
      def imageList = new ArrayList<String>()
      def imagesCallback = new ImagesCallback(imageList, false)
      def imageListResult = new ImageList()
      imageListResult.setItems([buildImage("my-image-1", false),
                                buildImage("my-image-2", false),
                                buildImage("my-image-3", false),
                                buildImage("my-other-image-1", false),
                                buildImage("my-other-image-2", false),
                                buildImage("my-other-image-3", false)])

    when:
      imagesCallback.onSuccess(imageListResult, null)

    then:
      imageList == [buildImage("my-image-1", false),
                    buildImage("my-image-2", false),
                    buildImage("my-image-3", false),
                    buildImage("my-other-image-1", false),
                    buildImage("my-other-image-2", false),
                    buildImage("my-other-image-3", false)]
  }

  private Image buildImage(String imageName, boolean deprecated) {
    return new Image(name: imageName, deprecated: new DeprecationStatus(state: deprecated ? "DEPRECATED" : null))
  }
}
