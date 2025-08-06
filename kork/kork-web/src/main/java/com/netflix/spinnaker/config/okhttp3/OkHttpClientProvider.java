/*
 * Copyright 2020 Netflix, Inc.
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

import static java.lang.String.format;

import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OkHttpClientProvider {

  private final List<OkHttpClientBuilderProvider> providers;

  @Autowired
  public OkHttpClientProvider(List<OkHttpClientBuilderProvider> providers) {
    this.providers = providers;
  }

  /**
   * Consults the provider impls to decide which provider class can build a client for the given
   * service config and delegates to that provider to build a client.
   *
   * @param service service configuration
   * @return okHttpClient
   */
  public OkHttpClient getClient(ServiceEndpoint service) {
    return getClient(service, List.of());
  }

  public OkHttpClient getClient(ServiceEndpoint service, List<Interceptor> interceptors) {
    OkHttpClient.Builder builder = findProvider(service).get(service);
    interceptors.forEach(builder::addInterceptor);
    return builder.build();
  }

  private OkHttpClientBuilderProvider findProvider(ServiceEndpoint service) {
    return providers.stream()
        .filter(provider -> provider.supports(service))
        .findFirst()
        .orElseThrow(
            () ->
                new SystemException(
                    format("No client provider found for url (%s)", service.getBaseUrl())));
  }
}
