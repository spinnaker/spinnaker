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

import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates GoogleCloudBuildAccounts */
@Component
@ConditionalOnProperty("gcb.enabled")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class GoogleCloudBuildAccountFactory {
  private final GoogleCredentialsService credentialService;
  private final GoogleCloudBuildClient.Factory googleCloudBuildClientFactory;
  private final GoogleCloudBuildCache.Factory googleCloudBuildCacheFactory;
  private final GoogleCloudBuildParser googleCloudBuildParser;

  GoogleCloudBuildAccount build(GoogleCloudBuildProperties.Account account) {
    GoogleCredentials credentials = getCredentials(account);

    GoogleCloudBuildClient client =
        googleCloudBuildClientFactory.create(credentials, account.getProject());
    return new GoogleCloudBuildAccount(
        client,
        googleCloudBuildCacheFactory.create(account.getName()),
        googleCloudBuildParser,
        new GoogleCloudBuildArtifactFetcher(client));
  }

  private GoogleCredentials getCredentials(GoogleCloudBuildProperties.Account account) {
    String jsonKey = account.getJsonKey();
    if (StringUtils.isEmpty(jsonKey)) {
      return credentialService.getApplicationDefault();
    } else {
      return credentialService.getFromKey(jsonKey);
    }
  }
}
