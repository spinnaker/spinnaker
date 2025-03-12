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
 * A simple HTTP client abstraction for use by plugins to talk to other Spinnaker services.
 *
 * <p>Many plugins will need to communicate with other Spinnaker services. Supplying plugins with a
 * Retrofit client or similar, and including all of its dependencies as a global plugin contract,
 * would be cause for a potential dependency management nightmare. HttpClient is used as an
 * abstraction layer over what HTTP libraries we use internally to talk between services, allowing
 * us to change the internals over time easier without affecting the plugin community.
 *
 * <p>Since this is an abstraction, plugins will not need to configure their own HTTP clients with
 * keystores/truststores, etc, as operators will be defining this once for the Spinnaker service
 * that the plugin is for.
 *
 * <p>It is not mandatory for plugins to use this HttpClient for all HTTP/RPC needs. If a plugin
 * wants to use Spring's WebClient, Retrofit, or something else, they are still able to do so.
 *
 * <pre>{@code
 * // Internal services can be retrieved by name.
 * HttpClient front50Client = httpClientProvider.getInternalService("front50");
 *
 * Response response = front50Client.get(new Request("getApplication", "/v2/applications/gate"));
 *
 * Application app = response.getBody(Application.class);
 * }</pre>
 *
 * TODO(rz): Add async api as well?
 */
@Beta
public interface HttpClient {
  @Nonnull
  Response get(@Nonnull Request request);

  @Nonnull
  Response post(@Nonnull Request request);

  @Nonnull
  Response put(@Nonnull Request request);

  @Nonnull
  Response delete(@Nonnull Request request);

  @Nonnull
  Response patch(@Nonnull Request request);
}
