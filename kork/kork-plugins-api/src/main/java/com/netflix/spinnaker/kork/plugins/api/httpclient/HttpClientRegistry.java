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
package com.netflix.spinnaker.kork.plugins.api.httpclient;

import com.netflix.spinnaker.kork.annotations.Beta;
import javax.annotation.Nonnull;

/**
 * Contains a bucket of configured {@link HttpClient}s for use within an extension.
 *
 * <p>There are two categories of HttpClients, one for internal Spinnaker services, and another for
 * extension-defined http clients.
 */
@Beta
public interface HttpClientRegistry {

  /**
   * Get an extension-defined {@link HttpClient} by name.
   *
   * <p>The client must first be configured before it can be retrieved from the registry.
   *
   * @param name The name of the HttpClient
   * @return The configured HttpClient
   */
  @Nonnull
  HttpClient get(@Nonnull String name);

  /**
   * Get an internal Spinnaker service {@link HttpClient}.
   *
   * @param name The name of the Spinnaker service you want to talk to
   * @return The internal service client
   */
  @Nonnull
  HttpClient getInternalService(@Nonnull String name);

  /**
   * Configure a new external {@link HttpClient}.
   *
   * <p>An HttpClient must be configured prior to being used.
   *
   * @param name A unique name for the client, scoped within an extension
   * @param baseUrl The base URL of the HTTP client
   * @param config Additional configuration for the client
   */
  void configure(@Nonnull String name, @Nonnull String baseUrl, @Nonnull HttpClientConfig config);
}
