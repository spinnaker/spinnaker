/*
 * Copyright 2021 Salesforce.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesJobStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KubernetesJobProviderTest {
  KubernetesManifestProvider mockManifestProvider;
  AccountCredentialsProvider credentialsProvider;
  AccountCredentials accountCredentials;
  KubernetesCredentials mockCredentials;

  @BeforeEach
  public void setup() {
    mockManifestProvider = mock(KubernetesManifestProvider.class);
    credentialsProvider = mock(AccountCredentialsProvider.class);
    accountCredentials = mock(AccountCredentials.class);
    mockCredentials = mock(KubernetesCredentials.class);

    doReturn(mockCredentials).when(accountCredentials).getCredentials();
    doReturn(accountCredentials).when(credentialsProvider).getCredentials(anyString());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testFailedJobWithContainerLogsAvailable(boolean detailedPodStatus) {
    // setup
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base-with-completions.yml"), KubernetesManifest.class);
    KubernetesManifest overlay =
        Yaml.loadAs(getResource("failed-job.yml"), KubernetesManifest.class);
    testManifest.putAll(overlay);

    doReturn(
            KubernetesManifestContainer.builder()
                .account("mock_account")
                .name("a")
                .manifest(testManifest)
                .build())
        .when(mockManifestProvider)
        .getManifest(anyString(), anyString(), anyString(), anyBoolean());

    doReturn(ImmutableList.of(testManifest)).when(mockCredentials).list(any(), isNull(), any());

    // when
    KubernetesJobProvider kubernetesJobProvider =
        new KubernetesJobProvider(credentialsProvider, mockManifestProvider, detailedPodStatus);
    KubernetesJobStatus jobStatus = kubernetesJobProvider.collectJob("mock_account", "a", "b");

    // then
    assertNotNull(jobStatus.getJobState());
    assertEquals(/* expected= */ JobState.Failed, /* actual= */ jobStatus.getJobState());

    assertThat(jobStatus.getMessage()).isEqualTo("Job has reached the specified backoff limit");
    assertThat(jobStatus.getReason()).isEqualTo("BackoffLimitExceeded");

    if (detailedPodStatus) {
      assertThat(jobStatus.getPods().size()).isEqualTo(1);
    } else {
      assertThat(jobStatus.getPods()).isEmpty();
    }

    assertThat(jobStatus.getFailureDetails())
        .isEqualTo(
            "Pod: 'hello' had errors.\n"
                + " Container: 'some-container-name' exited with code: 1.\n"
                + " Status: Error.\n"
                + " Logs: Failed to download the file: foo.\n"
                + "GET Request failed with status code', 404, 'Expected', <HTTPStatus.OK: 200>)\n");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testFailedJobWithoutContainerLogs(boolean detailedPodStatus) {
    // setup
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base-with-completions.yml"), KubernetesManifest.class);
    KubernetesManifest overlay =
        Yaml.loadAs(getResource("runjob-deadline-exceeded.yml"), KubernetesManifest.class);
    testManifest.putAll(overlay);
    doReturn(
            KubernetesManifestContainer.builder()
                .account("mock_account")
                .name("a")
                .manifest(testManifest)
                .build())
        .when(mockManifestProvider)
        .getManifest(anyString(), anyString(), anyString(), anyBoolean());
    doReturn(ImmutableList.of(testManifest)).when(mockCredentials).list(any(), isNull(), any());

    // when
    KubernetesJobProvider kubernetesJobProvider =
        new KubernetesJobProvider(credentialsProvider, mockManifestProvider, detailedPodStatus);
    KubernetesJobStatus jobStatus = kubernetesJobProvider.collectJob("mock_account", "a", "b");

    // then
    assertNotNull(jobStatus.getJobState());
    assertEquals(/* expected= */ JobState.Failed, /* actual= */ jobStatus.getJobState());

    assertThat(jobStatus.getMessage()).isEqualTo("Job was active longer than specified deadline");
    assertThat(jobStatus.getReason()).isEqualTo("DeadlineExceeded");

    assertNull(jobStatus.getFailureDetails());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCollectJobWithoutPod(boolean detailedPodStatus) {
    // setup
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base-with-completions.yml"), KubernetesManifest.class);
    doReturn(
            KubernetesManifestContainer.builder()
                .account("mock_account")
                .name("a")
                .manifest(testManifest)
                .build())
        .when(mockManifestProvider)
        .getManifest(anyString(), anyString(), anyString(), anyBoolean());
    doReturn(ImmutableList.of()).when(mockCredentials).list(any(), isNull(), any());

    KubernetesJobProvider kubernetesJobProvider =
        new KubernetesJobProvider(credentialsProvider, mockManifestProvider, detailedPodStatus);

    assertDoesNotThrow(() -> kubernetesJobProvider.collectJob("mock_account", "location", "id"));
  }

  private String getResource(String name) {
    try {
      return Resources.toString(
          KubernetesJobProviderTest.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
