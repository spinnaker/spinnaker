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
import lombok.NonNull;

@JsonTypeInfo(use= JsonTypeInfo.Id.NAME, include= JsonTypeInfo.As.PROPERTY, property = "type")
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface CanaryMetricSetQueryConfig {

  /**
   * @deprecated Use getCustomInlineTemplate instead.
   */
  @Deprecated
  default String getCustomFilter() {
    return null;
  }

  // Optionally defines a metric-specific query template. Takes precedence over customFilterTemplate.
  // It is expanded by using the key/value pairs in extendedScopeParams as the variable bindings.
  default String getCustomInlineTemplate() {
    return null;
  }

  // Optionally refers by name to a FreeMarker template defined in the canary config top-level 'templates' map. It is
  // expanded by using the key/value pairs in extendedScopeParams as the variable bindings. Once expanded, the
  // resulting filter is used when composing the query.
  default String getCustomFilterTemplate() {
    return null;
  }

  @NonNull String getServiceType();
}
