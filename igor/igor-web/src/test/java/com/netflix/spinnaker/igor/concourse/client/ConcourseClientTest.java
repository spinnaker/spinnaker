/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.concourse.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.net.UnknownHostException;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.jupiter.api.Test;

class ConcourseClientTest {
  @Test
  void connectException() {
    assertThatThrownBy(
            () ->
                new ConcourseClient(
                    "http://test",
                    "test",
                    "test",
                    new OkHttp3ClientConfiguration(
                        new OkHttpClientConfigurationProperties(),
                        null,
                        HttpLoggingInterceptor.Level.BASIC,
                        null,
                        null,
                        null)))
        .hasRootCauseInstanceOf(UnknownHostException.class);
  }
}
