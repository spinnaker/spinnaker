/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.docker.registry.controllers;

import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/dockerRegistry/images", "/titus/images"})
public class DockerRegistryImageLookupController extends AbstractDockerRegistryLookupController {

  @Override
  public Map<String, Object> generateArtifact(
      String registry, Object repository, Object tag, Object labels) {
    String reference = registry + "/" + repository + ":" + tag;
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("name", repository);
    artifact.put("type", "docker");
    artifact.put("version", tag);
    artifact.put("reference", reference);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("registry", registry);
    metadata.put("labels", labels);
    artifact.put("metadata", metadata);

    return artifact;
  }

  @Override
  protected String getTaggedImageKey(String account, String repository, String tag) {
    return Keys.getTaggedImageKey(account, repository, tag);
  }

  @Override
  protected String getImageIdKey(String pattern) {
    return Keys.getImageIdKey(pattern);
  }

  @Override
  protected String getTaggedImageNamespace() {
    return Keys.Namespace.TAGGED_IMAGE.getNs();
  }
}
