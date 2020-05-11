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

import javax.annotation.Nonnull;

/** Represents a service endpoint URL and name. */
public class DefaultServiceEndpoint implements ServiceEndpoint {

  /** Name of the service */
  @Nonnull private final String name;

  /** Base API url */
  @Nonnull private final String baseUrl;

  public DefaultServiceEndpoint(@Nonnull String name, @Nonnull String baseUrl) {
    this.name = name;
    this.baseUrl = baseUrl;
  }

  public String getName() {
    return name;
  }

  public String getBaseUrl() {
    return baseUrl;
  }
}
