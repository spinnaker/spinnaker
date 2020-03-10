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
package com.netflix.spinnaker.kork.plugins.api;

import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClient;
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientRegistry;
import com.netflix.spinnaker.kork.plugins.api.yaml.YamlResourceLoader;
import javax.annotation.Nonnull;

/**
 * A convenience interface for accessing plugin SDK services.
 *
 * <p>If an extension needs any services, this interface can be included as a constructor parameter
 * and the implementation will be injected into the extension.
 *
 * <pre>{@code
 * public class MyExtension {
 *
 *   private final PluginSdks pluginSdks;
 *
 *   public MyExtension(PluginSdks pluginSdks) {
 *     this.pluginSdks = pluginSdks;
 *   }
 * }
 * }</pre>
 */
public interface PluginSdks {

  /** Get the {@link HttpClientRegistry}, containing all configured {@link HttpClient}s. */
  @Beta
  @Nonnull
  HttpClientRegistry http();

  /** Get the {@link YamlResourceLoader}, util to load yml resources. */
  @Beta
  @Nonnull
  YamlResourceLoader yamlResourceLoader();
}
