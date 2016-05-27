/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.security

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient

public class DockerRegistryCredentials {
  private final DockerRegistryClient client;
  private List<String> repositories;
  private final boolean reloadRepositories;

  public DockerRegistryCredentials(DockerRegistryClient client, List<String> repositories) {
    this.client = client;
    if (!repositories) {
      this.reloadRepositories = true
      // Don't load the repositories yet, as it delays application startup time.
    } else {
      this.reloadRepositories = false
      this.repositories = repositories
    }
  }

  public DockerRegistryClient getClient() {
    return client;
  }

  public List<String> getRepositories() {
    if (reloadRepositories) {
      repositories = client.getCatalog()?.repositories;
    }
    return repositories
  }
}
