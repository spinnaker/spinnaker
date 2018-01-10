package com.netflix.kayenta.atlas.model;

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

import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class Backend {

  @NotNull
  @Getter
  private String cname;

  @NotNull
  @Getter
  private String deployment;

  @NotNull
  @Getter
  private String dataset;

  @NotNull
  @Getter
  private List<String> environments;

  @NotNull
  @Getter
  private List<String> regions;

  // TODO: Should be a Duration (but that can't parse the minute format PT1M)
  @NotNull
  @Getter
  private String step;

  @NotNull
  @Getter
  private String description;

  public String getUri(String method, String deployment, String dataset, String region, String environment) {
    String ret = cname
      .replace("$(deployment)", deployment)
      .replace("$(dataset)", dataset)
      .replace("$(region)", region)
      .replace("$(env)", environment);
    return method + "://" + ret;
  }
}
