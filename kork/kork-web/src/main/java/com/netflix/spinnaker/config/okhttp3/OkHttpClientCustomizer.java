/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config.okhttp3;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Beans of this type may customize a {@link OkHttpClient.Builder} prototype.
 *
 * @see RefreshableOkHttpClientBuilderProvider
 * @see com.netflix.spinnaker.config.OkHttpClientComponents#okHttpClientBuilder(ObjectProvider)
 */
@NonnullByDefault
@FunctionalInterface
public interface OkHttpClientCustomizer {
  void customize(OkHttpClient.Builder builder);
}
