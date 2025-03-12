/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.model

class DockerImage {

  public static final String IMAGE_NAME_SEPARATOR = ":"

  String imageName
  String imageVersion
  String imageDigest

  DockerImage(String imageId) {
    if (!imageId) throw new IllegalArgumentException("Invalid docker image id specified: ${imageId}")
    String[] imageNameParts = imageId.split(IMAGE_NAME_SEPARATOR)
    if (imageNameParts.size() != 2 && imageNameParts.size() != 3) throw new IllegalArgumentException("Invalid docker image id specified: ${imageId}")
    this.imageName = imageNameParts[0]
    if(imageNameParts.size() == 2){
      this.imageVersion = imageNameParts[1]
    } else {
      this.imageDigest = imageNameParts[1..2].join(IMAGE_NAME_SEPARATOR)
    }
  }

  static class DockerImageResolver {

    private static final String IMAGE_NAME_SEPARATOR = ":"

    /**
     * Docker Image registered: e703cdfcd21a as dockerregistry.us-east-1.dyntest.netflix.net:7001/engtools.dockerfile-test:master-201506020033-trusty-7366606
     * @param image
     * @return
     */
    static DockerImage resolveImage(String image) {
      String[] imageNameParts = image.split(IMAGE_NAME_SEPARATOR)

      if( imageNameParts.size() == 2) {
        return new DockerImage(
          imageName: imageNameParts[0],
          imageVersion: imageNameParts[1]
        )
      } else {
        return new DockerImage(
          imageName: imageNameParts[0],
          imageDigest: imageNameParts[1..2].join(IMAGE_NAME_SEPARATOR)
        )
      }
    }
  }
}
