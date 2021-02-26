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

class DockerRegistryCredentials {
  private final DockerRegistryClient client
  private List<String> repositories
  private final boolean reloadRepositories
  private final boolean trackDigests
  private final boolean inspectDigests
  private final boolean sortTagsByDate
  private List<String> skip

  DockerRegistryCredentials(DockerRegistryClient client, List<String> repositories, boolean trackDigests, boolean inspectDigests, List<String> skip, boolean sortTagsByDate) {
    this.client = client
    this.trackDigests = trackDigests
    this.inspectDigests = inspectDigests
    this.skip = skip
    if (!repositories) {
      this.reloadRepositories = true
      // Don't load the repositories yet, as it delays application startup time.
    } else {
      this.reloadRepositories = false
      this.repositories = repositories
    }
    this.sortTagsByDate = sortTagsByDate
  }

  DockerRegistryClient getClient() {
    return client
  }

  String getRegistry() {
    return client
  }

  boolean getTrackDigests() {
    return trackDigests
  }

  boolean getInspectDigests() {
    return inspectDigests
  }

  boolean getSortTagsByDate() {
    return sortTagsByDate
  }

  List<String> getSkip(){
    return skip
  }

  List<String> getRepositories() {
    if (reloadRepositories) {
      repositories = client.getCatalog()?.repositories
    }
    return repositories
  }
}
