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

package com.netflix.kayenta.google.security;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString(callSuper = true)
@Slf4j
public class GoogleJsonClientFactory extends GoogleClientFactory {

  @Getter private final String jsonKey;

  public GoogleJsonClientFactory(String project, String jsonKey) {
    super(project);

    this.jsonKey = jsonKey;
  }

  @Override
  protected GoogleCredentials getCredentials(Collection<String> scopes) throws IOException {
    log.debug(
        "Loading credentials for project {} from json key, with scopes {}.", getProject(), scopes);

    InputStream credentialStream = new ByteArrayInputStream(jsonKey.getBytes("UTF-8"));

    return GoogleCredentials.fromStream(credentialStream).createScoped(scopes);
  }
}
