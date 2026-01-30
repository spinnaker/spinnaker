/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.canary.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.*;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanaryAnalysisResult {

  // Todo: (mgraff) How to describe pre- and post-filter results?
  // Todo: (mgraff) Add a place to return additional timeseries data
  // Todo: (mgraff) Add a way to describe graph annotations on canary, baseline, and additional TS

  @NotNull @Getter private String name;

  @NotNull @Getter private Map<String, String> tags;

  @NotNull @Getter private String id;

  @NotNull @Getter private String classification;

  @Getter private String classificationReason;

  @NotNull @Getter private List<String> groups;

  @NotNull @Getter private Map<String, Object> experimentMetadata;

  @NotNull @Getter private Map<String, Object> controlMetadata;

  @NotNull @Getter private Map<String, Object> resultMetadata;

  @Getter @Builder.Default private boolean critical = false;

  @Getter @Builder.Default private boolean muted = false;
}
