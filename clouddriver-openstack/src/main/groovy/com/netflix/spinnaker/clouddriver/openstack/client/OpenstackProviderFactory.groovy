/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.api.OSClient
import org.openstack4j.core.transport.Config
import org.openstack4j.model.common.Identifier
import org.openstack4j.openstack.OSFactory

/**
 * Builds the appropriate {@link OpenstackClientProvider} based on the configuration.
 */
class OpenstackProviderFactory {

  static OpenstackClientProvider createProvider(OpenstackNamedAccountCredentials credentials) {
    OSClient osClient
    OpenstackClientProvider provider
    Config config = credentials.insecure ? Config.newConfig().withSSLVerificationDisabled() : Config.newConfig()
    if (AccountType.V2.value() == credentials.accountType) {
      osClient = OSFactory.builderV2()
        .withConfig(config)
        .endpoint(credentials.endpoint)
        .credentials(credentials.username, credentials.password)
        .tenantName(credentials.tenantName)
        .authenticate()
      provider = new OpenstackClientV2Provider(osClient)
    } else if (AccountType.V3.value() == credentials.accountType) {
      osClient = OSFactory.builderV3()
        .withConfig(config)
        .endpoint(credentials.endpoint)
        .credentials(credentials.username, credentials.password, Identifier.byName(credentials.domainName))
        .scopeToProject(Identifier.byName(credentials.tenantName), Identifier.byName(credentials.domainName))
        .authenticate()
      provider = new OpenstackClientV3Provider(osClient)
    } else {
      throw new IllegalArgumentException("Unknown account type ${credentials.accountType}")
    }
    provider
  }

  static enum AccountType {

    V2, V3

    String value() {
      return toString().toLowerCase()
    }
  }

}
