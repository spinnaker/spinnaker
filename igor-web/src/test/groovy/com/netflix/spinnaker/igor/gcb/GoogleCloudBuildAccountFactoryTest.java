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

import static org.mockito.Mockito.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoogleCloudBuildAccountFactoryTest {
  private GoogleCredentialService googleCredentialService = mock(GoogleCredentialService.class);
  private GoogleCloudBuildClient.Factory googleCloudBuildClientFactory =
      mock(GoogleCloudBuildClient.Factory.class);
  private GoogleCloudBuildCache.Factory googleCloudBuildCacheFactory =
      mock(GoogleCloudBuildCache.Factory.class);
  private GoogleCloudBuildParser googleCloudBuildParser = new GoogleCloudBuildParser();

  private GoogleCredential googleCredential = mock(GoogleCredential.class);
  private GoogleCloudBuildClient googleCloudBuildClient = mock(GoogleCloudBuildClient.class);

  private GoogleCloudBuildAccountFactory googleCloudBuildAccountFactory =
      new GoogleCloudBuildAccountFactory(
          googleCredentialService,
          googleCloudBuildClientFactory,
          googleCloudBuildCacheFactory,
          googleCloudBuildParser);

  @Test
  public void applicationDefaultCredentials() {
    GoogleCloudBuildProperties.Account accountConfig = getBaseAccount();
    accountConfig.setJsonKey("");

    when(googleCredentialService.getApplicationDefault()).thenReturn(googleCredential);
    when(googleCloudBuildClientFactory.create(eq(googleCredential), any(String.class)))
        .thenReturn(googleCloudBuildClient);

    GoogleCloudBuildAccount account = googleCloudBuildAccountFactory.build(accountConfig);

    verify(googleCredentialService).getApplicationDefault();
    verify(googleCredentialService, never()).getFromKey(any());
    verify(googleCloudBuildClientFactory).create(eq(googleCredential), any(String.class));
  }

  @Test
  public void jsonCredentials() {
    GoogleCloudBuildProperties.Account accountConfig = getBaseAccount();
    accountConfig.setJsonKey("/path/to/file");

    when(googleCredentialService.getFromKey("/path/to/file")).thenReturn(googleCredential);
    when(googleCloudBuildClientFactory.create(eq(googleCredential), any(String.class)))
        .thenReturn(googleCloudBuildClient);

    GoogleCloudBuildAccount account = googleCloudBuildAccountFactory.build(accountConfig);

    verify(googleCredentialService, never()).getApplicationDefault();
    verify(googleCredentialService).getFromKey("/path/to/file");
    verify(googleCloudBuildClientFactory).create(eq(googleCredential), any(String.class));
  }

  private GoogleCloudBuildProperties.Account getBaseAccount() {
    GoogleCloudBuildProperties.Account accountConfig = new GoogleCloudBuildProperties.Account();
    accountConfig.setName("test-account");
    accountConfig.setProject("test-project");
    return accountConfig;
  }
}
