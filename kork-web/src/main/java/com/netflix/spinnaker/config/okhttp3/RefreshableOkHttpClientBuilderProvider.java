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

import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@ConditionalOnProperty("ok-http-client.refreshable-keys.enabled")
@NonnullByDefault
@RequiredArgsConstructor
public class RefreshableOkHttpClientBuilderProvider implements OkHttpClientBuilderProvider {
  private final ObjectFactory<OkHttpClient.Builder> builderFactory;

  @Override
  public OkHttpClient.Builder get(ServiceEndpoint service) {
    return builderFactory.getObject();
  }
}
