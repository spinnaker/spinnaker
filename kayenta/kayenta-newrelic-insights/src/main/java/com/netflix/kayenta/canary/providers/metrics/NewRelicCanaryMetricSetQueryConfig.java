/*
 * Copyright 2018 Adobe
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
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.StringUtils;

@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("newrelic")
public class NewRelicCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "newrelic";

  @Nullable @Getter private String q;

  @Nullable @Getter private String select;

  @Nullable @Getter private String customInlineTemplate;

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
