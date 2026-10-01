/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP client settings for fetching user-supplied URLs (e.g. {@code #fromUrl}), bound from {@code
 * user-configured-url-restrictions.http-client-properties}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConfiguredHttpClientProperties {
  @Builder.Default private boolean enableRetry = true;
  @Builder.Default private int maxRetryAttempts = 1;
  @Builder.Default private int retryInterval = 5000;
  @Builder.Default private int timeoutMillis = 30000;
}
