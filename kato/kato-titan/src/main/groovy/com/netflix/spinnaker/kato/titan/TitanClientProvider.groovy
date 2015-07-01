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

package com.netflix.spinnaker.kato.titan

import com.netflix.spinnaker.kato.titan.credentials.NetflixTitanCredentials
import com.netflix.spinnaker.kato.titan.credentials.config.CredentialsConfig
import com.netflix.titanclient.TitanClient
import com.netflix.titanclient.TitanClientFactory
import com.netflix.titanclient.TitanEndpoint

/**
 * @author sthadeshwar
 */
class TitanClientProvider {

  private final CredentialsConfig titanConfig

  TitanClientProvider(CredentialsConfig titanConfig) {
    this.titanConfig = titanConfig
  }

  TitanClient getTitanClient(String account, String region) {
    TitanEndpoint titanEndpoint = new TitanEndpoint(account, region)
    TitanClientFactory.newInstance(titanEndpoint)
  }

  TitanClient getTitanClient(NetflixTitanCredentials account, String region) {
    TitanEndpoint titanEndpoint = new TitanEndpoint(account.name, region)
    TitanClientFactory.newInstance(titanEndpoint)
  }
}
