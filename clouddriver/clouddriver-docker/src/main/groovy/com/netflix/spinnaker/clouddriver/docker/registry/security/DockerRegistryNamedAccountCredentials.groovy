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
import com.netflix.spinnaker.clouddriver.docker.registry.exception.DockerRegistryConfigException
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.docker.service.DockerOkClientProvider
import com.netflix.spinnaker.kork.docker.service.DockerRegistryClient
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * TODO: Properties in this class are duplicated in HelmOciDockerArtifactAccount and
 * DockerRegistryClient. Future refactoring needed to reduce duplication.
 */
@Slf4j
class DockerRegistryNamedAccountCredentials extends AbstractAccountCredentials<DockerRegistryCredentials> {
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
    boolean inspectDigests
    boolean sortTagsByDate
    boolean insecureRegistry
    List<String> repositories
    List<String> skip
    String catalogFile
    String repositoriesRegex
    List<String> helmOciRepositories
    Permissions permissions
    DockerOkClientProvider dockerOkClientProvider
    ServiceClientProvider serviceClientProvider

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

    Builder inspectDigests(boolean inspectDigests) {
      this.inspectDigests = inspectDigests
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

    Builder repositoriesRegex(String repositoriesRegex) {
      this.repositoriesRegex = repositoriesRegex
      return this
    }

    Builder helmOciRepositories(List<String> helmOciRepositories) {
      this.helmOciRepositories = helmOciRepositories
      return this
    }

    Builder permissions(Permissions permissions) {
      this.permissions = permissions
      this
    }

    Builder dockerOkClientProvider(DockerOkClientProvider dockerOkClientProvider) {
      this.dockerOkClientProvider = dockerOkClientProvider
      return this
    }

    Builder serviceClientProvider(ServiceClientProvider serviceClientProvider) {
      this.serviceClientProvider = serviceClientProvider
      return this
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
        inspectDigests,
        sortTagsByDate,
        catalogFile,
        repositoriesRegex,
        insecureRegistry,
        null,
        helmOciRepositories,
        permissions,
        dockerOkClientProvider,
        serviceClientProvider)
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
                                        boolean inspectDigests,
                                        boolean sortTagsByDate,
                                        String catalogFile,
                                        String repositoriesRegex,
                                        boolean insecureRegistry,
                                        DockerOkClientProvider dockerOkClientProvider,
                                        ServiceClientProvider serviceClientProvider) {
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
      inspectDigests,
      sortTagsByDate,
      catalogFile,
      repositoriesRegex,
      insecureRegistry,
      null,
      null,
      dockerOkClientProvider,
      serviceClientProvider)
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
                                        boolean inspectDigests,
                                        boolean sortTagsByDate,
                                        String catalogFile,
                                        String repositoriesRegex,
                                        boolean insecureRegistry,
                                        List<String> requiredGroupMembership,
                                        List<String> helmOciRepositories,
                                        Permissions permissions,
                                        DockerOkClientProvider dockerOkClientProvider,
                                        ServiceClientProvider serviceClientProvider) {
    if (!accountName) {
      throw new IllegalArgumentException("Docker Registry account must be provided with a name.")
    }

    if (repositories && catalogFile) {
      throw new IllegalArgumentException("repositories and catalogFile may not be specified together.")
    }

    if (repositoriesRegex && (repositories || catalogFile)) {
      throw new IllegalArgumentException("repositoriesRegex may not be specified at the same time than repositories or catalogFile.")
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
    this.serviceClientProvider = serviceClientProvider

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
    this.repositoriesRegex = repositoriesRegex
    this.trackDigests = trackDigests
    this.inspectDigests = inspectDigests
    this.sortTagsByDate = sortTagsByDate
    this.insecureRegistry = insecureRegistry;
    this.skip = skip ?: []
    this.permissions = permissions ?: buildPermissionsFromRequiredGroupMembership(requiredGroupMembership)
    this.credentials = buildCredentials(repositories, catalogFile, dockerconfigFile, helmOciRepositories)
  }

  @JsonIgnore
  List<String> getRepositories() {
    return credentials.repositories
  }

  @Override
  String getName() {
    return accountName
  }

  String getRegistry() {
    return registry
  }

  @JsonIgnore
  String getBasicAuth() {
    return this.credentials?.client?.basicAuth ?: ""
  }

  @CompileStatic
  @JsonIgnore
  List<String> getTags(String repository) {
    def tags = credentials.client.getTags(repository).tags
    if (sortTagsByDate) {
      tags = KeyBasedSorter.sort(tags, { String t -> getCreationDate(repository, t) }, Comparator.reverseOrder())
    }
    tags
  }

  @CompileStatic
  private Instant getCreationDate(String repository, String tag) {
    try {
      return credentials.client.getCreationDate(repository, tag)
    } catch (Exception e) {
      log.warn("Unable to fetch tag creation date, reason: {} (tag: {}, repository: {})", e.message, tag, repository)
      return Instant.EPOCH;
    }
  }

  String getV2Endpoint() {
    return "$address/v2"
  }

  boolean getTrackDigests() {
    return trackDigests
  }

  boolean getInspectDigests() {
    return inspectDigests
  }

  int getCacheThreads() {
    return cacheThreads
  }

  long getCacheIntervalSeconds() {
    return cacheIntervalSeconds
  }

  DockerRegistryCredentials getCredentials() {
    return credentials
  }

  @Override
  String getCloudProvider() {
    return CLOUD_PROVIDER
  }

  private DockerRegistryCredentials buildCredentials(List<String> repositories, String catalogFile, File dockerconfigFile, List<String> helmOciRepositories) {
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
        .dockerconfigFile(dockerconfigFile)
        .repositoriesRegex(repositoriesRegex)
        .insecureRegistry(insecureRegistry)
        .okClientProvider(dockerOkClientProvider)
        .serviceClientProvider(serviceClientProvider)
        .build()

      return new DockerRegistryCredentials(client, repositories, trackDigests, inspectDigests, skip, sortTagsByDate, helmOciRepositories)
    } catch (SpinnakerHttpException e) {
      if(e.getResponseCode() == 404) {
        throw new DockerRegistryConfigException("No repositories specified for ${name}, and the provided endpoint ${address} does not support /_catalog.")
      } else {
        throw e
      }
    }
  }

  private static Permissions buildPermissionsFromRequiredGroupMembership(List<String> requiredGroupMembership) {
    if (requiredGroupMembership?.empty ?: true) {
      return Permissions.EMPTY
    }
    def builder = new Permissions.Builder()
    requiredGroupMembership.forEach {
      builder.add(Authorization.READ, it).add(Authorization.WRITE, it)
    }
    builder.build()
  }

  private static final String CLOUD_PROVIDER = "dockerRegistry"
  private final String accountName
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
  final boolean inspectDigests
  final boolean sortTagsByDate
  final int cacheThreads
  final long cacheIntervalSeconds
  final long clientTimeoutMillis
  final int paginateSize
  final boolean insecureRegistry
  @JsonIgnore
  final DockerRegistryCredentials credentials
  final Permissions permissions
  final List<String> skip
  final String catalogFile
  final String repositoriesRegex
  final DockerOkClientProvider dockerOkClientProvider
  final ServiceClientProvider serviceClientProvider
}
