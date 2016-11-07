/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.security

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory

class GoogleCommonCredentialUtils {
  static getCredentials (HttpTransport httpTransport, JsonFactory jsonFactory, String jsonKey, String scope) {
    InputStream credentialStream = new ByteArrayInputStream(jsonKey.getBytes("UTF-8"))

    return GoogleCredential.fromStream(credentialStream, httpTransport, jsonFactory)
      .createScoped(Collections.singleton(scope))
  }
}
