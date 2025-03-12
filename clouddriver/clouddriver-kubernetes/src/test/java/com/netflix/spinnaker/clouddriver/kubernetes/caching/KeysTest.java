/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.CacheKey;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class KeysTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "kubernetes.v2:infrastructure:secret:k8s:spin:spinnaker",
        "kubernetes.v2:logical:applications:spinnaker",
        "kubernetes.v2:logical:clusters:k8s:docs:docs-site"
      })
  void roundTripParse(String key) {
    Optional<CacheKey> parsed = Keys.parseKey(key);

    assertThat(parsed).isPresent();
    assertThat(parsed.get().toString()).isEqualTo(key);
  }
}
