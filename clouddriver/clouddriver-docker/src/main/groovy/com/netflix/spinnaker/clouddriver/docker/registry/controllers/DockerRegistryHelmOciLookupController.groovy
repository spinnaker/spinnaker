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

package com.netflix.spinnaker.clouddriver.docker.registry.controllers

import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping

@RestController
@RequestMapping(["/dockerRegistry/charts"])
class DockerRegistryHelmOciLookupController extends AbstractDockerRegistryLookupController {

  @Override
  Map generateArtifact(String registry, def repository, def tag, def labels) {
    String reference = "${registry}/${repository}:${tag}"
    [
      name      : repository,
      type      : "helm/image",
      version   : tag,
      reference : reference,
      metadata  : [ registry: registry, labels: labels ]
    ]
  }

  @Override
  protected String getTaggedImageKey(String account, String repository, String tag) {
    return Keys.getHelmOciTaggedImageKey(account, repository, tag)
  }

  @Override
  protected String getImageIdKey(String pattern) {
    return Keys.getImageIdKey(pattern)
  }

  @Override
  protected String getTaggedImageNamespace() {
    return Keys.Namespace.TAGGED_HELM_OCI_IMAGE.ns
  }
}
