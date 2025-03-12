/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.config.okhttp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {OkHttpClient.class, OkHttpClientConfigurationProperties.class})
public class OkHttpClientProviderTest {

  @Mock private DefaultOkHttpClientBuilderProvider defaultProvider;

  @Mock private ServiceEndpoint service;

  private OkHttpClient.Builder builder;

  @Mock private Interceptor interceptor;

  @Mock private Interceptor interceptor2;

  private OkHttpClientProvider clientProvider;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    builder = new OkHttpClient.Builder();
    clientProvider = new OkHttpClientProvider(List.of(defaultProvider));
  }

  @Test
  void getClient_withInterceptors_shouldAddInterceptors() {
    when(defaultProvider.supports(service)).thenReturn(true);
    when(defaultProvider.get(service)).thenReturn(builder);

    OkHttpClient result = clientProvider.getClient(service, List.of(interceptor, interceptor2));

    assertEquals(result.interceptors().size(), 2);
    assertEquals(result.interceptors().get(0), interceptor);
    assertEquals(result.interceptors().get(1), interceptor2);
  }

  @Test
  void getClient_noProviderFound_shouldThrowException() {
    when(defaultProvider.supports(service)).thenReturn(false);
    when(service.getBaseUrl()).thenReturn("http://example.com");

    SystemException exception =
        assertThrows(
            SystemException.class,
            () -> clientProvider.getClient(service),
            "Expected an exception if no provider supports the service");
    assertEquals(
        "No client provider found for url (http://example.com)",
        exception.getMessage(),
        "The exception message should indicate no provider was found.");
  }
}
