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

import com.netflix.spinnaker.clouddriver.dcos.DcosClientCompositeKey
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import mesosphere.dcos.client.Config

class DcosClusterCredentials {
  private final static DEFAULT_SECRET_STORE = 'default'
  final String name
  final String account
  final String cluster
  final String dcosUrl
  final String secretStore
  final List<DcosConfigurationProperties.LinkedDockerRegistryConfiguration> dockerRegistries
  final Config dcosConfig

  private DcosClusterCredentials(Builder builder) {
    name = builder.key.cluster
    account = builder.key.account
    cluster = builder.key.cluster
    dcosUrl = builder.dcosUrl
    secretStore = builder.secretStore ? builder.secretStore : DEFAULT_SECRET_STORE
    dockerRegistries = builder.dockerRegistries
    dcosConfig = builder.dcosConfig
  }

  public static Builder builder() {
    return new Builder()
  }

  public static class Builder {
    private DcosClientCompositeKey key
    private String dcosUrl
    private String secretStore
    private List<DcosConfigurationProperties.LinkedDockerRegistryConfiguration> dockerRegistries
    private Config dcosConfig

    public Builder key(DcosClientCompositeKey key) {
      this.key = key
      this
    }

    public Builder dcosUrl(String dcosUrl) {
      this.dcosUrl = dcosUrl
      this
    }

    public Builder secretStore(String secretStore) {
      this.secretStore = secretStore
      this
    }

    public Builder dockerRegistries(List<DcosConfigurationProperties.LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries
      this
    }

    public Builder dcosConfig(Config dcosConfig) {
      this.dcosConfig = dcosConfig
      this
    }

    public DcosClusterCredentials build() {
      return new DcosClusterCredentials(this)
    }
  }
}
