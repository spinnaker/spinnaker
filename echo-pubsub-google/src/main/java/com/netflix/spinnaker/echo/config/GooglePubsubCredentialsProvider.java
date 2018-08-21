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

package com.netflix.spinnaker.echo.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class GooglePubsubCredentialsProvider implements CredentialsProvider {

  private String jsonPath;

  @Override
  public Credentials getCredentials() throws IOException {
    if (StringUtils.isNotEmpty(this.jsonPath)) {
      try {
        return ServiceAccountCredentials.fromStream(new FileInputStream(this.jsonPath));
      } catch (IOException e) {
        log.error("Could not import Google Pubsub json credentials: {}", e.getMessage());
      }
    } else {
      try {
        return GoogleCredentials.getApplicationDefault();
      } catch (IOException e) {
        log.error("Could not import default application credentials: {}", e.getMessage());
      }
    }
    throw new IllegalStateException("Unable to authenticate to Google Pubsub.");
  }
}
