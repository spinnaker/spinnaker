/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.security.aws

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.security.NamedAccountCredentials


class RefreshingCredentialsProvider implements AWSCredentialsProvider {
  private final long ttl

  private final NamedAccountCredentials<DiscoveryAwareAmazonCredentials> account

  private Long lastCredentialRefresh
  private AWSCredentials credentials

  RefreshingCredentialsProvider(NamedAccountCredentials<DiscoveryAwareAmazonCredentials> account, long ttl) {
    this.account = account
    this.ttl = ttl
  }

  @Override
  AWSCredentials getCredentials() {
    if (!lastCredentialRefresh || new Date().time - lastCredentialRefresh > ttl) {
      refresh()
    }
    this.credentials
  }

  @Override
  void refresh() {
    this.lastCredentialRefresh = new Date().time
    this.credentials = account.credentials.credentials
  }
}
