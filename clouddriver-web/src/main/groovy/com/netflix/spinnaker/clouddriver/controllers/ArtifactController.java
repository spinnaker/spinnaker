/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/artifacts")
public class ArtifactController {
  private ArtifactCredentialsRepository artifactCredentialsRepository;

  @Autowired
  public ArtifactController(Optional<ArtifactCredentialsRepository> artifactCredentialsRepository) {
    this.artifactCredentialsRepository = artifactCredentialsRepository.orElse(null);
  }

  @RequestMapping(method = RequestMethod.GET, value = "/credentials")
  List<ArtifactCredentials> list() {
    if (artifactCredentialsRepository == null) {
      return new ArrayList<>();
    } else {
      return artifactCredentialsRepository.getAllCredentials();
    }
  }
}
