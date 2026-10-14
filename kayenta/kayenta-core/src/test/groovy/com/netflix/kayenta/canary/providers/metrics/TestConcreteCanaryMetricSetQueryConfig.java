/*
 * Copyright 2026 Google, Inc.
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

package com.netflix.kayenta.canary.providers.metrics;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Minimal concrete leaf subclass of {@link AbstractCanaryMetricSetQueryConfig}, used only to
 * exercise the abstract class's shared template-resolution logic directly in tests -- {@code
 * kayenta-core} doesn't depend on any of the real provider modules (they depend on it), so it can't
 * reuse one of those concrete classes as its own test subject.
 */
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@JsonTypeName("test-concrete")
public class TestConcreteCanaryMetricSetQueryConfig extends AbstractCanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "test-concrete";

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }
}
