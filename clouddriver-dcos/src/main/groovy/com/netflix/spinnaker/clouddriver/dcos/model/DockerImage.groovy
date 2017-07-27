/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.model

class DockerImage {
  private static final String IMAGE_NAME_SEPARATOR = ":"
  private static final String DEFAULT_REGISTRY = "docker.io"

  String imageRegistry
  String imageName
  String imageVersion

  String getImageId() {
    return [imageRegistry, imageName, imageVersion].join(IMAGE_NAME_SEPARATOR)
  }

  static DockerImage resolveImage(String image) {
    String[] imageNameParts = image.split(IMAGE_NAME_SEPARATOR)

    if (imageNameParts.size() == 2) {
      return new DockerImage(
        imageRegistry: DEFAULT_REGISTRY,
        imageName: imageNameParts[0],
        imageVersion: imageNameParts[1]
      )
    } else if (imageNameParts.size() == 3) {
      return new DockerImage(
        imageRegistry: imageNameParts[0],
        imageName: imageNameParts[1],
        imageVersion: imageNameParts[2]
      )
    } else {
      throw new IllegalArgumentException("Invalid docker image id specified: ${image}")
    }
  }
}
