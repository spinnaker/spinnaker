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

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import org.apache.log4j.Logger

class ImagesCallback<ImageList> extends JsonBatchCallback<ImageList> {
  protected static final Logger log = Logger.getLogger(this)

  private List<Map> imageList
  private boolean prune

  public ImagesCallback(List<Map> imageList, boolean prune) {
    this.imageList = imageList
    this.prune = prune
  }

  @Override
  void onSuccess(ImageList imageListResult, HttpHeaders responseHeaders) throws IOException {
    if (prune) {
      imageList.addAll(pruneImageList(imageListResult))
    } else {
      imageListResult.items.each { image ->
        imageList << image
      }
    }
  }

  @Override
  void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
    log.error e.getMessage()
  }

  private static List<Map> pruneImageList(ImageList imageListResult) {
    /*
      Build a map from pruned image names to sorted sets of full image representations (images are sorted by name).

      An ImageList like the following:
      backports-debian-7-wheezy-v20141017
      backports-debian-7-wheezy-v20141021
      backports-debian-7-wheezy-v20141108
      debian-7-wheezy-v20141017
      debian-7-wheezy-v20141021
      debian-7-wheezy-v20141108

      Should result in this map:
      {backports-debian-7-wheezy: [backports-debian-7-wheezy-v20141017, backports-debian-7-wheezy-v20141021, backports-debian-7-wheezy-v20141108],
       debian-7-wheezy: [debian-7-wheezy-v20141017, debian-7-wheezy-v20141021, debian-7-wheezy-v20141108]}
      Each item in the lists above will be a full image representation; just the names are shown here.
     */
    Map<String, Set<String>> map = new HashMap<String, Set<String>>()

    imageListResult.items.each { image ->
      String fullImageName = image.name
      // TODO(duftler): Verify the naming convention and devise consistent pruning logic.
      // Public coreos images break the naming convention of the others.
      int delimiter = fullImageName.startsWith("coreos-")
                      ? fullImageName.indexOf('-', 7)
                      : fullImageName.lastIndexOf('-')
      String nameWithoutDate = delimiter != -1 ? fullImageName.substring(0, delimiter) : fullImageName

      if (!map[nameWithoutDate]) {
        map[nameWithoutDate] = new TreeSet<Image>(new Comparator<Image>() {
          @Override
          int compare(Image image1, Image image2) {
            image1.name <=> image2.name
          }
        })
      }

      map[nameWithoutDate] << image
    }

    // Collect the final item in each sorted set and sort the resulting list.
    map.collect {
      it.value.last()
    }.sort {
      it.name
    }
  }
}