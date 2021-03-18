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

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.Image;
import com.netflix.spinnaker.clouddriver.model.ImageProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/images")
public class ImageController {

  @Autowired List<ImageProvider> imageProviders;

  @RequestMapping(value = "/{provider}/{imageId}", method = RequestMethod.GET)
  Image getImage(@PathVariable String provider, @PathVariable String imageId) {

    List<ImageProvider> imageProviderList =
        imageProviders.stream()
            .filter(imageProvider -> imageProvider.getCloudProvider().equals(provider))
            .collect(Collectors.toList());

    if (imageProviderList.isEmpty()) {
      throw new NotFoundException("ImageProvider for provider " + provider + " not found.");
    } else if (imageProviderList.size() > 1) {
      throw new IllegalStateException(
          "Found multiple ImageProviders for provider "
              + provider
              + ". Multiple ImageProviders for a single provider are not supported.");
    } else {
      return imageProviderList
          .get(0)
          .getImageById(imageId)
          .orElseThrow(() -> new NotFoundException("Image not found (id: " + imageId + ")"));
    }
  }
}
