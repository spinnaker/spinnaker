/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudbuild.v1.CloudBuild;
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Creates GoogleCloudBuildAccounts
 */
@Component
@ConditionalOnProperty("gcb.enabled")
@RequiredArgsConstructor
public class GoogleCloudBuildAccountFactory {
  private final GoogleCredentialService credentialService;
  private final CloudBuildFactory cloudBuildFactory;
  private final GoogleCloudBuildExecutor googleCloudBuildExecutor;

  public GoogleCloudBuildAccount build(GoogleCloudBuildProperties.Account account) {
    GoogleCredential credential = getCredential(account);
    String applicationName = getApplicationName();
    CloudBuild cloudBuild = cloudBuildFactory.getCloudBuild(credential, applicationName);

    return new GoogleCloudBuildAccount(account.getProject(), cloudBuild, googleCloudBuildExecutor);
  }

  private String getApplicationName() {
    return Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("Unknown");
  }

  private GoogleCredential getCredential(GoogleCloudBuildProperties.Account account) {
    String jsonKey = account.getJsonKey();
    if (StringUtils.isEmpty(jsonKey)) {
      return credentialService.getApplicationDefault();
    } else {
      return credentialService.getFromKey(jsonKey);
    }
  }
}
