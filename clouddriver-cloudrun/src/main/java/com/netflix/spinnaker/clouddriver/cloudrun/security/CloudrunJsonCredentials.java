/*
 * Copyright 2022 OpsMx Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.security;

import com.google.api.services.run.v1.CloudRunScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.clouddriver.googlecommon.security.GoogleCommonCredentialUtils;

public class CloudrunJsonCredentials extends CloudrunCredentials {

  private final String jsonKey;

  public CloudrunJsonCredentials(String project, String jsonKey) {
    super(project);
    this.jsonKey = jsonKey;
  }

  @Override
  public GoogleCredentials getCredentials() {
    return GoogleCommonCredentialUtils.getCredentials(jsonKey, CloudRunScopes.CLOUD_PLATFORM);
  }

  public final String getJsonKey() {
    return jsonKey;
  }
}
