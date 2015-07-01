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

package com.netflix.spinnaker.oort.titan.credentials

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.oort.titan.credentials.config.CredentialsConfig

/**
 * @author sthadeshwar
 */
class NetflixTitanCredentials implements AccountCredentials<TitanCredentials> {

  private final String name
  private final List<Region> regions

  NetflixTitanCredentials(String name, List<CredentialsConfig.Region> regions) {
    this.name = name
    this.regions = regions
  }

  @Override
  String getName() {
    name
  }

  @Override
  TitanCredentials getCredentials() {
    new TitanCredentials() {}
  }

  @Override
  String getProvider() {
    "titan"
  }

  List<CredentialsConfig.Region> getRegions() {
    return regions
  }

  static class Region {
    String name
  }
}
