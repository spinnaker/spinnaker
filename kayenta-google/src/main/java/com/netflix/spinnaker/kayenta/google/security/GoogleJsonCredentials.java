/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.kayenta.google.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.monitoring.v3.MonitoringScopes;
import com.google.api.services.storage.StorageScopes;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.ToString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@ToString(callSuper = true)
public class GoogleJsonCredentials extends GoogleCredentials {

  @Getter
  private final String jsonKey;

  public GoogleJsonCredentials(String project, String jsonKey) {
    super(project);

    this.jsonKey = jsonKey;
  }

  @Override
  protected GoogleCredential getCredential(HttpTransport httpTransport, JsonFactory jsonFactory) throws IOException {
    Set<String> scopes = Sets.newHashSet(MonitoringScopes.all());
    scopes.addAll(StorageScopes.all());

    InputStream credentialStream = new ByteArrayInputStream(jsonKey.getBytes("UTF-8"));

    return GoogleCredential.fromStream(credentialStream, httpTransport, jsonFactory).createScoped(scopes);
  }
}
