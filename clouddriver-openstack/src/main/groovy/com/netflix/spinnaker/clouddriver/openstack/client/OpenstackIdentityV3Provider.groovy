/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.api.OSClient
import org.openstack4j.core.transport.Config
import org.openstack4j.model.common.Identifier
import org.openstack4j.model.identity.v3.Token
import org.openstack4j.openstack.OSFactory

class OpenstackIdentityV3Provider implements OpenstackIdentityProvider, OpenstackRequestHandler {

  OpenstackNamedAccountCredentials credentials
  Token token = null

  OpenstackIdentityV3Provider(OpenstackNamedAccountCredentials credentials) {
    this.credentials = credentials
  }

  @Override
  OSClient buildClient() {
    handleRequest {
      Config config = credentials.insecure ? Config.newConfig().withSSLVerificationDisabled() : Config.newConfig()
      OSFactory.builderV3()
        .withConfig(config)
        .endpoint(credentials.authUrl)
        .credentials(credentials.username, credentials.password, Identifier.byName(credentials.domainName))
        .scopeToProject(Identifier.byName(credentials.projectName), Identifier.byName(credentials.domainName))
        .authenticate()
    }
  }

  @Override
  OSClient getClient() {
    if (!token || tokenExpired) {
      synchronized (this) {
        if (!token || tokenExpired) {
          token = buildClient().token
        }
      }
    }
    OSFactory.clientFromToken(token)
  }

  @Override
  String getTokenId() {
    token?.id
  }

  @Override
  boolean isTokenExpired() {
    long now = System.currentTimeMillis()
    long expires = token.expires.time
    now >= expires
  }

  /**
   * Returns configuration based regions if provided, otherwise will use the
   * API to look up regions and return a list.
   * @return
   */
  @Override
  List<String> getAllRegions() {
    credentials.regions ?: handleRequest {
      client.identity().regions().list()?.collect { it.id }
    }
  }

  @Override
  OSClient getRegionClient(String region) {
    client.useRegion(region)
  }

}
