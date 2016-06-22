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

import org.openstack4j.api.OSClient
import org.openstack4j.model.identity.v2.Access
import org.openstack4j.openstack.OSFactory

/**
 * Provides access to the Openstack V2 API.
 */
class OpenstackClientV2Provider extends OpenstackClientProvider {

  Access access
  List<String> regions

  /**
   * Default constructor .. Requires regions are configured externally
   * as v2 openstack API doesn't support looking up regions.
   * @param client
   * @param regions - List of region ids
     */
  OpenstackClientV2Provider(OSClient.OSClientV2 client, List<String> regions) {
    this.access = client.access
    this.regions = regions
  }

  @Override
  OSClient getClient() {
    OSFactory.clientFromAccess(access)
  }

  @Override
  String getTokenId() {
    access.token.id
  }

  //TODO v2 specific operations

  @Override
  List<String> getAllRegions() {
    regions
  }
}
