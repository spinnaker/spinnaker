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

package com.netflix.kayenta.canary.providers.metrics;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.*;
import org.springframework.util.StringUtils;

@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("stackdriver")
public class StackdriverCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "stackdriver";

  @Getter private String resourceType;

  @NotNull @Getter private String metricType;

  @Getter private String crossSeriesReducer;

  @Getter private String perSeriesAligner;

  @NotNull @Getter private List<String> groupByFields;

  /**
   * @deprecated Use customInlineTemplate instead.
   */
  @Deprecated @Getter private String customFilter;

  @Getter private String customInlineTemplate;

  @Getter private String customFilterTemplate;

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

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }
}
