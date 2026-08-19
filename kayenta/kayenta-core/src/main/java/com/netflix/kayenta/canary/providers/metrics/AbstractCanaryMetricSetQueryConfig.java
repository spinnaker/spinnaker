/*
 * Copyright 2018 Google, Inc.
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

import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.util.StringUtils;

/**
 * Base class for {@link CanaryMetricSetQueryConfig} implementations, providing the shared
 * customInlineTemplate/customFilterTemplate fields and escaping logic common to every provider, so
 * individual provider implementations don't each redeclare them.
 */
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class AbstractCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  @Getter private String customInlineTemplate;

  @Getter private String customFilterTemplate;

  // Lombok's @SuperBuilder does not generate builder()/toBuilder() convenience methods on
  // abstract classes (there is no concrete builder impl to instantiate for an abstract type) --
  // only concrete leaf classes get them. Declaring this abstract lets each concrete subclass's
  // generated toBuilder() satisfy it covariantly, so the call below dispatches polymorphically.
  public abstract AbstractCanaryMetricSetQueryConfigBuilder<?, ?> toBuilder();

  @Override
  public CanaryMetricSetQueryConfig cloneWithEscapedInlineTemplate() {
    if (StringUtils.isEmpty(customInlineTemplate)) {
      return this;
    } else {
      return this.toBuilder()
          .customInlineTemplate(customInlineTemplate.replace("${", "$\\{"))
          .build();
    }
  }
}
