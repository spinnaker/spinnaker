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

package com.netflix.spinnaker.clouddriver.titan
import com.netflix.spinnaker.clouddriver.titan.credentials.NetflixTitanCredentials
import com.netflix.titanclient.TitanClient
import groovy.transform.Canonical

class TitanClientProvider {

  private final List<TitanClientHolder> titanClientHolders

  TitanClientProvider(List<TitanClientHolder> titanClientHolders) {
    this.titanClientHolders = titanClientHolders
  }

  TitanClient getTitanClient(NetflixTitanCredentials account, String region) {
    TitanClientHolder titanClientHolder = titanClientHolders.find { it.account == account.name && it.region == region }
    if (!titanClientHolder) {
      throw new IllegalArgumentException("No titan client registered for account ${account.name} and region ${region}")
    }
    titanClientHolder.titanClient
  }

  @Canonical
  static class TitanClientHolder {
    final String account
    final String region
    final TitanClient titanClient
  }
}
