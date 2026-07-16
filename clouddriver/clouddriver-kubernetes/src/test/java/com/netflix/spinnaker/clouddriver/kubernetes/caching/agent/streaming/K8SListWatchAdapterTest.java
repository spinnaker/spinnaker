/*
 * Copyright 2025 Wise, PLC.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class K8SListWatchAdapterTest {

  private DynamicKubernetesApi mockApi;
  private K8SListWatchAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    ApiClient mockClient = mock(ApiClient.class);
    adapter = new K8SListWatchAdapter("", "v1", "pods", mockClient);

    // Replace the internal api field with a mock
    mockApi = mock(DynamicKubernetesApi.class);
    Field apiField = K8SListWatchAdapter.class.getDeclaredField("api");
    apiField.setAccessible(true);
    apiField.set(adapter, mockApi);
  }

  @Test
  void listWithContinueFromSetsResourceVersionToNull() throws ApiException {
    // given
    KubernetesApiResponse<DynamicKubernetesListObject> mockResponse =
        mock(KubernetesApiResponse.class);
    when(mockResponse.throwsApiException()).thenReturn(mockResponse);
    when(mockResponse.getObject()).thenReturn(mock(DynamicKubernetesListObject.class));
    when(mockApi.list(any(ListOptions.class))).thenReturn(mockResponse);

    ArgumentCaptor<ListOptions> listOptionsCaptor = ArgumentCaptor.forClass(ListOptions.class);

    // when
    adapter.list(10, "12345", 100, "continue-token");

    // then
    // https://github.com/kubernetes/kubernetes/issues/85221#issuecomment-553748143
    verify(mockApi).list(listOptionsCaptor.capture());
    ListOptions capturedOptions = listOptionsCaptor.getValue();
    assertThat(capturedOptions.getResourceVersion()).isNull();
    assertThat(capturedOptions.getContinue()).isEqualTo("continue-token");
    assertThat(capturedOptions.getLimit()).isEqualTo(100);
    assertThat(capturedOptions.getTimeoutSeconds()).isEqualTo(10);
  }

  @Test
  void listWithoutContinueFromPreservesResourceVersion() throws ApiException {
    // given
    KubernetesApiResponse<DynamicKubernetesListObject> mockResponse =
        mock(KubernetesApiResponse.class);
    when(mockResponse.throwsApiException()).thenReturn(mockResponse);
    when(mockResponse.getObject()).thenReturn(mock(DynamicKubernetesListObject.class));
    when(mockApi.list(any(ListOptions.class))).thenReturn(mockResponse);

    ArgumentCaptor<ListOptions> listOptionsCaptor = ArgumentCaptor.forClass(ListOptions.class);

    // when
    adapter.list(10, "12345", 100, null);

    // then
    verify(mockApi).list(listOptionsCaptor.capture());
    ListOptions capturedOptions = listOptionsCaptor.getValue();
    assertThat(capturedOptions.getResourceVersion()).isEqualTo("12345");
    assertThat(capturedOptions.getContinue()).isNull();
    assertThat(capturedOptions.getLimit()).isEqualTo(100);
    assertThat(capturedOptions.getTimeoutSeconds()).isEqualTo(10);
  }

  @Test
  void listWithoutLimitDoesNotSetPaginationOptions() throws ApiException {
    // given
    KubernetesApiResponse<DynamicKubernetesListObject> mockResponse =
        mock(KubernetesApiResponse.class);
    when(mockResponse.throwsApiException()).thenReturn(mockResponse);
    when(mockResponse.getObject()).thenReturn(mock(DynamicKubernetesListObject.class));
    when(mockApi.list(any(ListOptions.class))).thenReturn(mockResponse);

    ArgumentCaptor<ListOptions> listOptionsCaptor = ArgumentCaptor.forClass(ListOptions.class);

    // when
    adapter.list(10, "12345", 0, "continue-token");

    // then
    verify(mockApi).list(listOptionsCaptor.capture());
    ListOptions capturedOptions = listOptionsCaptor.getValue();
    assertThat(capturedOptions.getResourceVersion()).isEqualTo("12345");
    assertThat(capturedOptions.getContinue()).isNull();
    assertThat(capturedOptions.getLimit()).isNull();
    assertThat(capturedOptions.getTimeoutSeconds()).isEqualTo(10);
  }
}
