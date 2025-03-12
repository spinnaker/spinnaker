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
package com.netflix.spinnaker.kork.plugins.update.internal

import okhttp3.OkHttpClient

/**
 * Provider class for [OkHttpClient] to use in the plugin framework.
 *
 * We need to share an OkHttpClient around the plugin framework codebase, but we don't want to wire up an OkHttpClient
 * Bean, since we would risk changing existing service configuration behavior. This class just wraps a single
 * configured instance of OkHttpClient.
 *
 * @param okHttpClient The proxied [OkHttpClient].
 */
class PluginOkHttpClientProvider(
  val okHttpClient: OkHttpClient
)
