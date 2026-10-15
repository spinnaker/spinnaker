/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.canary;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import java.util.Map;
import lombok.NonNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface CanaryMetricSetQueryConfig {

  /**
   * @deprecated Use getCustomInlineTemplate instead.
   */
  @Deprecated
  default String getCustomFilter() {
    return null;
  }

  /**
   * Optionally defines a metric-specific query template. Takes precedence over {@link
   * #getCustomFilterTemplate}. It is expanded by using the key/value pairs in {@link
   * CanaryScope#extendedScopeParams} as the variable bindings.
   *
   * @deprecated use {@link #getTemplate(CanaryConfig)} instead; retained for backward-compatible
   *     reads of existing configs and for the few call sites that still need the raw,
   *     un-resolved-against-the-named-templates-map field directly.
   */
  @Deprecated
  default String getCustomInlineTemplate() {
    return null;
  }

  /**
   * Optionally refers by name to a template defined in the canary config top-level {@link
   * CanaryConfig#templates} map. It is expanded by using the key/value pairs in {@link
   * CanaryScope#extendedScopeParams} as the variable bindings. Once expanded, the resulting filter
   * is used when composing the query.
   *
   * @deprecated use {@link #getTemplate(CanaryConfig)} instead; retained for backward-compatible
   *     reads of existing configs.
   */
  @Deprecated
  default String getCustomFilterTemplate() {
    return null;
  }

  /**
   * @deprecated renamed to {@link #cloneWithEscapedTemplate()}; retained so implementations outside
   *     this repo that only override this method keep escaping their inline template.
   */
  @Deprecated
  default CanaryMetricSetQueryConfig cloneWithEscapedInlineTemplate() {
    return this;
  }

  default CanaryMetricSetQueryConfig cloneWithEscapedTemplate() {
    return cloneWithEscapedInlineTemplate();
  }

  /**
   * Resolves the single raw template string to use for this metric's query, applying the {@code
   * template} (formerly {@code customInlineTemplate}) vs. {@code customFilterTemplate} precedence
   * and named-template-map lookup, and unescaping the result. Returns {@code null} when neither
   * field is set, or when a {@code customFilterTemplate} name doesn't resolve against {@code
   * canaryConfig}'s {@link CanaryConfig#getTemplates()} map (callers on the execution path use
   * {@link QueryConfigUtils#expandCustomFilter} which fails loudly in that case).
   *
   * <p>The default resolves through {@link #getCustomInlineTemplate()} and {@link
   * #getCustomFilterTemplate()} so implementations that predate the single {@code template} field
   * keep working unchanged.
   */
  default String getTemplate(CanaryConfig canaryConfig) {
    String inline = getCustomInlineTemplate();

    if (inline != null && !inline.isEmpty()) {
      return QueryConfigUtils.unescapeTemplate(inline);
    }

    String name = getCustomFilterTemplate();

    if (name != null && !name.isEmpty() && canaryConfig != null) {
      Map<String, String> templates = canaryConfig.getTemplates();

      if (templates != null && templates.get(name) != null) {
        return QueryConfigUtils.unescapeTemplate(templates.get(name));
      }
    }

    return null;
  }

  @NonNull
  String getServiceType();
}
