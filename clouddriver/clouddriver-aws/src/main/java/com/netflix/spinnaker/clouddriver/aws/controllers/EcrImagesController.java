/*
 * Copyright 2026 Moderne, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.controllers;

import com.netflix.spinnaker.clouddriver.aws.provider.view.EcrDockerTagResolver;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the ECR digest→semver-tag lookup over HTTP. Lives in {@code clouddriver-aws} so it loads
 * whenever AWS is enabled — independent of the ECS module, which is only loaded when ECS accounts
 * are configured.
 */
@RestController
@RequestMapping("/ecr/images")
public class EcrImagesController {
  private final EcrDockerTagResolver ecrDockerTagResolver;

  @Autowired
  public EcrImagesController(EcrDockerTagResolver ecrDockerTagResolver) {
    this.ecrDockerTagResolver = ecrDockerTagResolver;
  }

  @RequestMapping(value = "/resolveDockerTag", method = RequestMethod.GET)
  public Map<String, String> resolveDockerTag(@RequestParam("reference") String reference) {
    EcrDockerTagResolver.ResolveResult result = ecrDockerTagResolver.resolve(reference);
    return Map.of("resolvedTag", result.resolvedTag, "reference", result.resolvedReference);
  }

  @RequestMapping(value = "/resolveDockerTagByName", method = RequestMethod.GET)
  public Map<String, String> resolveDockerTagByName(
      @RequestParam("repository") String repository, @RequestParam("tag") String tag) {
    EcrDockerTagResolver.ResolveResult result = ecrDockerTagResolver.resolveByName(repository, tag);
    return Map.of("resolvedTag", result.resolvedTag, "reference", result.resolvedReference);
  }
}
