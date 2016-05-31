/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.config

import groovy.transform.ToString

@ToString(includeNames = true)
class DockerRegistryConfigurationProperties {
  @ToString(includeNames = true)
  static class ManagedAccount {
    String name
    String environment
    String accountType
    // Docker registry username.
    String username
    // Docker registry password.
    String password
    // File containing docker password.
    String passwordFile
    // Docker registry user email address.
    String email
    // Address of the registry.
    String address
    // How many threads to cache all provided repos on. Really only useful if you have a ton of repos.
    int cacheThreads
    // Timeout time in milliseconds for this repository. Default is 60,000 (1 minute).
    long clientTimeoutMillis
    // Paginate size for the docker repository /_catalog endpoint. Default is 100.
    int paginateSize
    // Track digest changes. This is _not_ recommended as it consumes a high QPM, and most registries are flaky.
    boolean trackDigests
    // List of all repositories to index. Can be of the form <user>/<repo>,
    // or <library> for repositories like 'ubuntu'.
    List<String> repositories
  }

  List<ManagedAccount> accounts = []
}
