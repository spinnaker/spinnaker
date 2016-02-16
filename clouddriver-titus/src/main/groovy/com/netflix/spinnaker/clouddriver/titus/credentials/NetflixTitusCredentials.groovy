/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.credentials

import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.security.TitusCredentials

class NetflixTitusCredentials implements AccountCredentials<TitusCredentials> {
  private static final String CLOUD_PROVIDER = Keys.PROVIDER

  final String name
  final String environment
  final String accountType
  final List<String> requiredGroupMembership = Collections.emptyList()

  private final List<TitusRegion> regions

  NetflixTitusCredentials(String name, String environment, String accountType, List<TitusRegion> regions) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.regions = regions?.asImmutable() ?: Collections.emptyList()
  }

  @Override
  TitusCredentials getCredentials() {
    new TitusCredentials() {}
  }

  @Override
  String getProvider() {
    getCloudProvider()
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }

  List<TitusRegion> getRegions() {
    regions
  }

}
