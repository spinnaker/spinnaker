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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.util.StringUtils;

/**
 * Base class for {@link CanaryMetricSetQueryConfig} implementations, providing the shared {@code
 * template}/{@code customFilterTemplate} fields and escaping logic common to every provider, so
 * individual provider implementations don't each redeclare them.
 */
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class AbstractCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  /**
   * Single per-metric query template. Replaces the old two-field {@code
   * customInlineTemplate}/{@code customFilterTemplate} split. {@code @JsonAlias} allows existing
   * stored JSON still using the legacy {@code customInlineTemplate} key to deserialize into this
   * field automatically; new writes always serialize under {@code template}.
   */
  @Getter
  @JsonAlias("customInlineTemplate")
  private String template;

  /**
   * @deprecated superseded by {@link #template}; refers by name to an entry in the canary config's
   *     top-level {@code templates} map. Retained only for backward-compatible reads of existing
   *     configs -- see {@link #getTemplate(CanaryConfig)}.
   */
  @Deprecated @Getter private String customFilterTemplate;

  /**
   * @deprecated use {@link #getTemplate()} instead; retained for backward-compatible reads of
   *     existing configs, and because some call sites still invoke this directly rather than
   *     through JSON deserialization. {@code @JsonIgnore}d so new writes serialize only under
   *     {@code template}, never duplicating the value under the legacy key.
   */
  @Deprecated
  @JsonIgnore
  public String getCustomInlineTemplate() {
    return template;
  }

  // Lombok's @SuperBuilder does not generate builder()/toBuilder() convenience methods on
  // abstract classes (there is no concrete builder impl to instantiate for an abstract type) --
  // only concrete leaf classes get them. Declaring this abstract lets each concrete subclass's
  // generated toBuilder() satisfy it covariantly, so the call below dispatches polymorphically.
  public abstract AbstractCanaryMetricSetQueryConfigBuilder<?, ?> toBuilder();

  @Override
  public CanaryMetricSetQueryConfig cloneWithEscapedTemplate() {
    if (StringUtils.isEmpty(template)) {
      return this;
    } else {
      return this.toBuilder().template(template.replace("${", "$\\{")).build();
    }
  }

  @Override
  public String getTemplate(CanaryConfig canaryConfig) {
    if (!StringUtils.isEmpty(template)) {
      return QueryConfigUtils.unescapeTemplate(template);
    }

    if (!StringUtils.isEmpty(customFilterTemplate)
        && canaryConfig != null
        && canaryConfig.getTemplates() != null) {
      String named = canaryConfig.getTemplates().get(customFilterTemplate);

      if (named != null) {
        return QueryConfigUtils.unescapeTemplate(named);
      }
    }

    return null;
  }
}
