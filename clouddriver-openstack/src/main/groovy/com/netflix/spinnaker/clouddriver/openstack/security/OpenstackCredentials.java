/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.security;

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.identity.v3.Token;

public class OpenstackCredentials {
  private final OSClientV3 client;
  private final Token token;

  public OpenstackCredentials(String username, String password, String tenantId, String endpoint) {
    // TODO: add support for keystone v2
    this.client = OSFactory.builderV3()
                           .endpoint(endpoint)
                           .credentials(username, password)
                           .scopeToProject(Identifier.byName(tenantId))
                           .authenticate();

    /**
     * Cached token can be used to create a client in cases where you would not want to re-authenticate
     *
     * @see org.openstack4j.openstack.OSFactory#clientFromToken(Token)
     *
     */
    this.token = client.getToken();
  }

  public OSClientV3 getClient() {
    return client;
  }

  public Token getToken() {
    return token;
  }
}
