/*
 * Copyright 2015 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.docker.registry.exception.DockerRegistryConfigException
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import retrofit.RetrofitError

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

public class DockerRegistryNamedAccountCredentials implements AccountCredentials<DockerRegistryCredentials> {
  public DockerRegistryNamedAccountCredentials(String accountName, String environment, String accountType,
                                               String address, String username, String password, String passwordFile, String email,
                                               int cacheThreads, long clientTimeoutMillis, int paginateSize, List<String> repositories) {
    this(accountName, environment, accountType, address, username, password, passwordFile, email, repositories, cacheThreads, clientTimeoutMillis, paginateSize, null)
  }

  public DockerRegistryNamedAccountCredentials(String accountName, String environment, String accountType,
                                               String address, String username, String password, String passwordFile, String email,
                                               List<String> repositories, int cacheThreads, long clientTimeoutMillis,
                                               int paginateSize, List<String> requiredGroupMembership) {
    if (!accountName) {
      throw new IllegalArgumentException("Docker Registry account must be provided with a name.")
    }
    this.accountName = accountName
    this.environment = environment
    this.accountType = accountType
    this.cacheThreads = cacheThreads ?: 1
    this.paginateSize = paginateSize ?: 100
    this.clientTimeoutMillis = clientTimeoutMillis ?: TimeUnit.MINUTES.toMillis(1)

    if (!address) {
      throw new IllegalArgumentException("Docker Registry account $accountName must provide an endpoint address.");
    } else {
      int addressLen = address.length();
      if (address[addressLen - 1] == '/') {
        address = address.substring(0, addressLen - 1)
        addressLen -= 1
      }
      // Strip the v2 endpoint, as the Docker API assumes it's not present.
      if (address.endsWith('/v2')) {
        address = address.substring(0, addressLen - 3)
      }
    }

    this.address = address
    if (address.startsWith('https://')) {
      this.registry = address.substring('https://'.length())
    } else if (address.startsWith('http://')) {
      this.registry = address.substring('http://'.length())
    } else {
      this.registry = address
    }
    this.username = username
    if (!password && passwordFile) {
      byte[] contents = Files.readAllBytes(Paths.get(passwordFile))
      password = new String(contents, StandardCharsets.UTF_8)
    }
    this.password = password
    this.email = email
    this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership)
    this.credentials = buildCredentials(repositories)
  }

  public List<String> getRepositories() {
    return credentials.repositories
  }

  @Override
  public String getName() {
    return accountName
  }

  public String getBasicAuth() {
    return this.credentials ?
      this.credentials.client ?
        this.credentials.client.basicAuth ?
          this.credentials.client.basicAuth :
          "" :
        "" :
      ""
  }

  public String getV2Endpoint() {
    return "$address/v2"
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER
  }

  private DockerRegistryCredentials buildCredentials(List<String> repositories) {
    try {
      DockerRegistryClient client = new DockerRegistryClient(address, email, username, password, clientTimeoutMillis, paginateSize)
      return new DockerRegistryCredentials(client, repositories)
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        throw new DockerRegistryConfigException("No repositories specified for ${name}, and the provided endpoint ${address} does not support /_catalog.")
      } else {
        throw e
      }
    }
  }

  private static final String CLOUD_PROVIDER = "dockerRegistry"
  final String accountName
  final String environment
  final String accountType
  final String address
  final String registry
  final String username
  final String password
  final String email
  final int cacheThreads
  final long clientTimeoutMillis
  final int paginateSize
  final DockerRegistryCredentials credentials
  final List<String> requiredGroupMembership
}
