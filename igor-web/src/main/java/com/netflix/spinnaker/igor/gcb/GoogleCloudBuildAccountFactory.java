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
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates GoogleCloudBuildAccounts */
@Component
@ConditionalOnProperty("gcb.enabled")
@RequiredArgsConstructor
public class GoogleCloudBuildAccountFactory {
  private final GoogleCredentialService credentialService;
  private final GoogleCloudBuildClient.Factory googleCloudBuildClientFactory;
  private final GoogleCloudBuildCache.Factory googleCloudBuildCacheFactory;
  private final GoogleCloudBuildParser googleCloudBuildParser;

  public GoogleCloudBuildAccount build(GoogleCloudBuildProperties.Account account) {
    GoogleCredential credential = getCredential(account);

    GoogleCloudBuildClient client =
        googleCloudBuildClientFactory.create(credential, account.getProject());
    return new GoogleCloudBuildAccount(
        client,
        googleCloudBuildCacheFactory.create(account.getName()),
        googleCloudBuildParser,
        new GoogleCloudBuildArtifactFetcher(client));
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
