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
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.*;

@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanaryConfig {

  @NotNull @Getter @Setter private Long createdTimestamp;

  @NotNull @Getter @Setter private Long updatedTimestamp;

  @NotNull @Getter @Setter private String createdTimestampIso;

  @NotNull @Getter @Setter private String updatedTimestampIso;

  @NotNull @Getter @Setter private String name;

  @Getter private String id;

  @NotNull @Getter private String description;

  @NotNull @Getter private String configVersion;

  @NotNull @Getter @Setter @Singular private List<String> applications;

  @NotNull @Getter private CanaryJudgeConfig judge;

  @NotNull @Singular @Getter private List<CanaryMetricConfig> metrics;

  @NotNull @Singular @Getter private Map<String, String> templates;

  @NotNull @Getter private CanaryClassifierConfig classifier;
}
