/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.netflix.spinnaker.clouddriver.ecs.model.EcsDockerImage;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.ImageRepositoryProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ecs/images")
public class EcsImagesController {
  private final List<ImageRepositoryProvider> imageRepositoryProviders;

  @Autowired
  public EcsImagesController(List<ImageRepositoryProvider> imageRepositoryProviders) {
    this.imageRepositoryProviders = imageRepositoryProviders;
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public List<EcsDockerImage> findImage(
      @RequestParam("q") String dockerImageUrl, HttpServletRequest request) {
    for (ImageRepositoryProvider provider : imageRepositoryProviders) {
      if (provider.handles(dockerImageUrl)) {
        return provider.findImage(dockerImageUrl);
      }
    }

    throw new Error(
        "The URL is not support by any of the providers. Currently enabled and supported providers are: "
            + imageRepositoryProviders.stream()
                .map(ImageRepositoryProvider::getRepositoryName)
                .collect(Collectors.joining(", "))
            + ".");
  }
}
