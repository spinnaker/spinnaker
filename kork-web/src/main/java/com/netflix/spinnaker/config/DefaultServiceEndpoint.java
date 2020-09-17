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
 *
 */

package com.netflix.spinnaker.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Represents a service endpoint URL and name. */
public class DefaultServiceEndpoint implements ServiceEndpoint {

  /** Name of the service */
  @Nonnull private final String name;

  /** Base API url */
  @Nonnull private final String baseUrl;

  /** Indicates whether the certificate/host verification is desired or not */
  private final boolean isSecure;

  /** Misc. config necessary for the service client. */
  @Nonnull private final Map<String, String> config;

  public DefaultServiceEndpoint(@Nonnull String name, @Nonnull String baseUrl) {
    this(name, baseUrl, new HashMap<>(), true);
  }

  public DefaultServiceEndpoint(@Nonnull String name, @Nonnull String baseUrl, boolean isSecure) {
    this(name, baseUrl, new HashMap<>(), isSecure);
  }

  public DefaultServiceEndpoint(
      @Nonnull String name, @Nonnull String baseUrl, @Nonnull Map<String, String> config) {
    this(name, baseUrl, config, true);
  }

  public DefaultServiceEndpoint(
      @Nonnull String name,
      @Nonnull String baseUrl,
      @Nonnull Map<String, String> config,
      boolean isSecure) {
    this.name = Objects.requireNonNull(name);
    this.baseUrl = Objects.requireNonNull(baseUrl);
    this.config = Objects.requireNonNull(config);
    this.isSecure = isSecure;
  }

  @Override
  @Nonnull
  public String getName() {
    return name;
  }

  @Override
  @Nonnull
  public String getBaseUrl() {
    return baseUrl;
  }

  @Nonnull
  @Override
  public Map<String, String> getConfig() {
    return config;
  }

  @Override
  public boolean isSecure() {
    return isSecure;
  }
}
