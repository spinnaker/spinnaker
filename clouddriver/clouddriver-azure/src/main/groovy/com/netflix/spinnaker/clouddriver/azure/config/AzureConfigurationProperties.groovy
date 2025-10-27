/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.config

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomImageStorage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.Canonical
import groovy.transform.ToString
import org.springframework.boot.context.properties.NestedConfigurationProperty

import javax.annotation.Nonnull

class AzureConfigurationProperties {

  @ToString(includeNames = true)
  @JsonTypeName("azure")
  static class ManagedAccount implements AccessControlledAccountDefinition {
    String name
    String environment
    String accountType
    String clientId
    String appKey
    String tenantId
    String subscriptionId
    List<String> regions
    List<AzureVMImage> vmImages
    List<AzureCustomImageStorage> customImages
    String defaultResourceGroup
    String defaultKeyVault
    Boolean useSshPublicKey
    String namingStrategy
    Permissions.Builder permissions = new Permissions.Builder()
    
    @Nonnull
    @Override
    Map<Authorization, Set<String>> getPermissions() {
      return permissions.build().unpack()
    }
  }

  List<ManagedAccount> accounts = []
  /**
   * health check related config settings
   */
  @Canonical
  static class HealthConfig {
    /**
     * flag to toggle verifying account health check. by default, account health check is enabled.
     */
    boolean verifyAccountHealth = true
  }
  @NestedConfigurationProperty
  final HealthConfig health = new HealthConfig()
}
