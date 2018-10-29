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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerOkClientProvider
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient
import com.netflix.spinnaker.clouddriver.docker.registry.exception.DockerRegistryConfigException
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.util.logging.Slf4j
import retrofit.RetrofitError

import java.util.concurrent.TimeUnit

@Slf4j
class DockerRegistryNamedAccountCredentials implements AccountCredentials<DockerRegistryCredentials> {
  static class Builder {
    String accountName
    String environment
    String accountType
    String address
    String username
    String password
    String passwordCommand
    File passwordFile
    File dockerconfigFile
    String email
    int cacheThreads
    long cacheIntervalSeconds
    long clientTimeoutMillis
    int paginateSize
    boolean trackDigests
    boolean sortTagsByDate
    boolean insecureRegistry
    List<String> repositories
    List<String> skip
    String catalogFile
    DockerOkClientProvider dockerOkClientProvider

    Builder() {}

    Builder accountName(String accountName) {
      this.accountName = accountName
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder address(String address) {
      this.address = address
      return this
    }

    Builder username(String username) {
      this.username = username
      return this
    }

    Builder password(String address) {
      this.password = address
      return this
    }

    Builder passwordCommand(String passwordCommand) {
      this.passwordCommand = passwordCommand
      return this
    }

    Builder passwordFile(String passwordFile) {
      if (passwordFile) {
        this.passwordFile = new File(passwordFile)
      } else {
        this.passwordFile = null
      }

      return this
    }

    Builder dockerconfigFile(String dockerconfigFile) {
      if (dockerconfigFile) {
        this.dockerconfigFile = new File(dockerconfigFile)
      } else {
        this.dockerconfigFile = null
      }

      return this
    }

    Builder email(String email) {
      this.email = email
      return this
    }

    Builder cacheThreads(int cacheThreads) {
      this.cacheThreads = cacheThreads
      return this
    }

    Builder cacheIntervalSeconds(long cacheIntervalSeconds) {
      this.cacheIntervalSeconds = cacheIntervalSeconds
      return this
    }

    Builder clientTimeoutMillis(long clientTimeoutMillis) {
      this.clientTimeoutMillis = clientTimeoutMillis
      return this
    }

    Builder paginateSize(int paginateSize) {
      this.paginateSize = paginateSize
      return this
    }

    Builder trackDigests(boolean trackDigests) {
      this.trackDigests = trackDigests
      return this
    }

    Builder sortTagsByDate(boolean sortTagsByDate) {
      this.sortTagsByDate = sortTagsByDate
      return this
    }

    Builder insecureRegistry(boolean insecureRegistry) {
      this.insecureRegistry = insecureRegistry
      return this
    }

    Builder repositories(List<String> repositories) {
      this.repositories = repositories
      return this
    }

    Builder skip(List<String> skip) {
      this.skip = skip
      return this
    }

    Builder catalogFile(String catalogFile) {
      this.catalogFile = catalogFile
      return this
    }

    Builder dockerOkClientProvider(DockerOkClientProvider dockerOkClientProvider) {
      this.dockerOkClientProvider = dockerOkClientProvider
    }

    DockerRegistryNamedAccountCredentials build() {
      return new DockerRegistryNamedAccountCredentials(accountName,
                                                       environment,
                                                       accountType,
                                                       address,
                                                       username,
                                                       password,
                                                       passwordCommand,
                                                       passwordFile,
                                                       dockerconfigFile,
                                                       email,
                                                       repositories,
                                                       skip,
                                                       cacheThreads,
                                                       cacheIntervalSeconds,
                                                       clientTimeoutMillis,
                                                       paginateSize,
                                                       trackDigests,
                                                       sortTagsByDate,
                                                       catalogFile,
                                                       insecureRegistry,
                                                       dockerOkClientProvider)
    }
  }

  DockerRegistryNamedAccountCredentials(String accountName,
                                        String environment,
                                        String accountType,
                                        String address,
                                        String username,
                                        String password,
                                        String passwordCommand,
                                        File passwordFile,
                                        File dockerconfigFile,
                                        String email,
                                        List<String> repositories,
                                        List<String> skip,
                                        int cacheThreads,
                                        long cacheIntervalSeconds,
                                        long clientTimeoutMillis,
                                        int paginateSize,
                                        boolean trackDigests,
                                        boolean sortTagsByDate,
                                        String catalogFile,
                                        boolean insecureRegistry,
                                        DockerOkClientProvider dockerOkClientProvider) {
    this(accountName,
         environment,
         accountType,
         address,
         username,
         password,
         passwordCommand,
         passwordFile,
         dockerconfigFile,
         email,
         repositories,
         skip,
         cacheThreads,
         cacheIntervalSeconds,
         clientTimeoutMillis,
         paginateSize,
         trackDigests,
         sortTagsByDate,
         catalogFile,
         insecureRegistry,
         null,
         dockerOkClientProvider)
  }

  DockerRegistryNamedAccountCredentials(String accountName,
                                        String environment,
                                        String accountType,
                                        String address,
                                        String username,
                                        String password,
                                        String passwordCommand,
                                        File passwordFile,
                                        File dockerconfigFile,
                                        String email,
                                        List<String> repositories,
                                        List<String> skip,
                                        int cacheThreads,
                                        long cacheIntervalSeconds,
                                        long clientTimeoutMillis,
                                        int paginateSize,
                                        boolean trackDigests,
                                        boolean sortTagsByDate,
                                        String catalogFile,
                                        boolean insecureRegistry,
                                        List<String> requiredGroupMembership,
                                        DockerOkClientProvider dockerOkClientProvider) {
    if (!accountName) {
      throw new IllegalArgumentException("Docker Registry account must be provided with a name.")
    }

    if (repositories && catalogFile) {
      throw new IllegalArgumentException("repositories and catalogFile may not be specified together.")
    }

    this.accountName = accountName
    this.environment = environment
    this.accountType = accountType
    this.passwordCommand = passwordCommand
    this.passwordFile = passwordFile
    this.cacheThreads = cacheThreads ?: 1
    this.cacheIntervalSeconds = cacheIntervalSeconds ?: 30
    this.paginateSize = paginateSize ?: 100
    this.clientTimeoutMillis = clientTimeoutMillis ?: TimeUnit.MINUTES.toMillis(1)
    this.dockerOkClientProvider = dockerOkClientProvider

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
    this.password = password
    this.email = email
    this.trackDigests = trackDigests
    this.sortTagsByDate = sortTagsByDate
    this.insecureRegistry = insecureRegistry;
    this.skip = skip ?: []
    this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership)
    this.credentials = buildCredentials(repositories, catalogFile)
  }

  @JsonIgnore
  List<String> getRepositories() {
    return credentials.repositories
  }

  @Override
  String getName() {
    return accountName
  }

  @JsonIgnore
  String getBasicAuth() {
    return this.credentials?.client?.basicAuth ?: ""
  }

  @JsonIgnore
  List<String> getTags(String repository) {
    def tags = credentials.client.getTags(repository).tags
    if (sortTagsByDate) {
      tags = tags.parallelStream().map({
        tag -> try {
          [date: credentials.client.getCreationDate(repository, tag), tag: tag]
        } catch (Exception e) {
          log.warn("Unable to fetch tag creation date, reason: {} (tag: {}, repository: {})", e.message, tag, repository)
          return [date: new Date(0), tag: tag]
        }
      }).toArray().sort {
        it.date
      }.reverse().tag
    }
    tags
  }

  String getV2Endpoint() {
    return "$address/v2"
  }

  @Override
  String getCloudProvider() {
    return CLOUD_PROVIDER
  }

  private DockerRegistryCredentials buildCredentials(List<String> repositories, String catalogFile) {
    try {
      DockerRegistryClient client = (new DockerRegistryClient.Builder())
        .address(address)
        .email(email)
        .username(username)
        .password(password)
        .passwordCommand(passwordCommand)
        .passwordFile(passwordFile)
        .clientTimeoutMillis(clientTimeoutMillis)
        .paginateSize(paginateSize)
        .catalogFile(catalogFile)
        .insecureRegistry(insecureRegistry)
        .okClientProvider(dockerOkClientProvider)
        .build()

      return new DockerRegistryCredentials(client, repositories, trackDigests, skip, sortTagsByDate)
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
  @JsonIgnore
  final String username
  @JsonIgnore
  final String password
  final String passwordCommand
  final File passwordFile
  final String email
  final boolean trackDigests
  final boolean sortTagsByDate
  final int cacheThreads
  final long cacheIntervalSeconds
  final long clientTimeoutMillis
  final int paginateSize
  final boolean insecureRegistry
  @JsonIgnore
  final DockerRegistryCredentials credentials
  final List<String> requiredGroupMembership
  final List<String> skip
  final String catalogFile
  final DockerOkClientProvider dockerOkClientProvider
}
