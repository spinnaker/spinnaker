/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId
import com.netflix.spinnaker.fiat.model.resources.Permissions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties.LinkedDockerRegistryConfiguration

class DcosAccountCredentials extends AbstractAccountCredentials<DcosCredentialMap> {
  private static final Logger LOGGER = LoggerFactory.getLogger(DcosAccountCredentials)
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  final String name
  final String account
  final String environment
  final String accountType
  final List<LinkedDockerRegistryConfiguration> dockerRegistries
  final List<String> requiredGroupMembership
  final Permissions permissions
  final List<DcosRegion> regions
  final Registry spectatorRegistry
  // Not really a fan of creating this just for use within deck, but it works for now
  final List<DcosClusterInfo> dcosClusters

  @JsonIgnore
  final DcosCredentialMap dcosClusterCredentials

  DcosAccountCredentials(String account,
                         String environment,
                         String accountType,
                         List<LinkedDockerRegistryConfiguration> dockerRegistries,
                         List<String> requiredGroupMembership,
                         Permissions permissions,
                         Registry spectatorRegistry,
                         List<DcosClusterCredentials> clusters) {
    this.name = account
    this.account = account
    this.environment = environment
    this.accountType = accountType
    this.dockerRegistries = dockerRegistries != null ? dockerRegistries : new ArrayList<>()
    this.requiredGroupMembership = requiredGroupMembership
    this.permissions = permissions
    this.spectatorRegistry = spectatorRegistry
    this.dcosClusterCredentials = new DcosCredentialMap(clusters)
    this.dcosClusters = clusters.collect({ new DcosClusterInfo(it.name, it.dcosUrl, it.dockerRegistries) })
    this.regions = clusters.collect({ new DcosRegion(it.name) })
  }

  static Builder builder() {
    return new Builder()
  }

  @JsonIgnore
  @Override
  DcosCredentialMap getCredentials() {
    dcosClusterCredentials
  }

  @JsonIgnore
  DcosClusterCredentials getCredentialsByCluster(String cluster) {
    dcosClusterCredentials.getCredentialsByCluster(cluster)
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }

  static class Builder {
    private String account
    private String environment
    private String accountType
    private List<LinkedDockerRegistryConfiguration> dockerRegistries
    private List<String> requiredGroupMembership
    private Permissions permissions
    private Registry spectatorRegistry
    private List<DcosClusterCredentials> clusterCredentials

    Builder account(String account) {
      this.account = account
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

    Builder dockerRegistries(List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder clusters(List<DcosClusterCredentials> clusterCredentials) {
      this.clusterCredentials = clusterCredentials
      return this
    }

    Builder permissions(final Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = []
        this.permissions = permissions
      }
      return this
    }

    Builder spectatorRegistry(Registry spectatorRegistry) {
      this.spectatorRegistry = spectatorRegistry
      return this
    }

    DcosAccountCredentials build() {
      if (!account) {
        throw new IllegalArgumentException("Account name for DC/OS provider is missing.")
      }

      if (!MarathonPathId.isPartValid(account)) {
        throw new IllegalArgumentException("Account name [${name}] is not valid for the DC/OS provider. Only lowercase letters, numbers, and dashes(-) are allowed.")
      }

      clusterCredentials.each {
        if (!MarathonPathId.isPartValid(it.name)) {
          clusterCredentials.remove(it)
          LOGGER.warn("Cluster name [${name}] is not valid for the DC/OS cluster. Only lowercase letters, numbers, and dashes(-) are allowed.")
        }
      }

      if (!dockerRegistries || dockerRegistries.size() <= 0) {
        throw new IllegalArgumentException("Docker registries for DC/OS account [${name}] missing.")
      }

      requiredGroupMembership = requiredGroupMembership ? Collections.unmodifiableList(requiredGroupMembership) : []

      new DcosAccountCredentials(account, environment, accountType, dockerRegistries, requiredGroupMembership, permissions, spectatorRegistry, clusterCredentials)
    }

  }

  public static class DcosRegion {
    public final String name

    public DcosRegion(String name) {
      if (name == null) {
        throw new IllegalArgumentException("Name must be specified.")
      }
      this.name = name
    }

    public String getName() { return name }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true
      }
      if (o == null || !(o instanceof DcosRegion)) {
        return false
      }

      DcosRegion dcosRegion = (DcosRegion) o

      name.equals(dcosRegion.name)
    }

    @Override
    public int hashCode() {
      name.hashCode()
    }
  }

  // Used only by deck, to make things easier for retrieving the cluster specific docker registry limitations
  private static class DcosClusterInfo {
    final String name
    final String url
    final List<LinkedDockerRegistryConfiguration> dockerRegistries

    DcosClusterInfo(String name, String url, List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.name = name
      this.url = url
      this.dockerRegistries = dockerRegistries
    }
  }
}
