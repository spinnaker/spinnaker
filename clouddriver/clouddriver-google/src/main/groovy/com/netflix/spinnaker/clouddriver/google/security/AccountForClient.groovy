/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.client.googleapis.services.AbstractGoogleClient

class AccountForClient {
  // Maps a compute instance to the account that it is for.
  // This is to facilitate scoping Spinnaker metrics to the account it is for.
  static final Map<AbstractGoogleClient, String> clientToAccount = [:]

  // This value is used by AWS provider, but should not appear in practice for GCP
  // other than in unit tests with mocked objects that did not go through a normal
  // factory construction.
  static final String UNKNOWN_ACCOUNT = "UNSPECIFIED_ACCOUNT"

  static void addGoogleClient(AbstractGoogleClient client, String account) {
    clientToAccount[client] = account
  }

  static String getAccount(AbstractGoogleClient client) {
    String account = clientToAccount[client]
    return account ? account : UNKNOWN_ACCOUNT
  }
}
