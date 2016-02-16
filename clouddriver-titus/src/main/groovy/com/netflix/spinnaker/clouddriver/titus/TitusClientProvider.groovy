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

package com.netflix.spinnaker.clouddriver.titus

import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import groovy.transform.Canonical

class TitusClientProvider {

  private final List<TitusClientHolder> titusClientHolders

  TitusClientProvider(List<TitusClientHolder> titusClientHolders) {
    this.titusClientHolders = titusClientHolders
  }

  TitusClient getTitusClient(NetflixTitusCredentials account, String region) {
    TitusClientHolder titusClientHolder = titusClientHolders.find { it.account == account.name && it.region == region }
    if (!titusClientHolder) {
      throw new IllegalArgumentException("No titus client registered for account ${account.name} and region ${region}")
    }
    titusClientHolder.titusClient
  }

  @Canonical
  static class TitusClientHolder {
    final String account
    final String region
    final TitusClient titusClient
  }
}
