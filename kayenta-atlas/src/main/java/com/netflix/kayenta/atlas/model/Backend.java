/*
 * Copyright 2018 Netflix, Inc.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
  private String target;

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

  public List<String> getTargets() {
    ArrayList<String> ret = new ArrayList<>();

    ret.add(target.replace("$(deployment)", deployment).replace("$(dataset)", dataset));

    if (regions != null) {
      ArrayList<String> newList = new ArrayList<>();
      for (String region : regions) {
        for (String item : ret) {
          newList.add(item.replace("$(region)", region));
        }
      }
      ret = newList;
    }

    if (environments != null) {
      ArrayList<String> newList = new ArrayList<>();
      for (String environment : environments) {
        for (String item : ret) {
          newList.add(item.replace("$(env)", environment));
        }
      }
      ret = newList;
    }

    return ret.stream().distinct().collect(Collectors.toList());
  }

  public String getUri(String scheme, String deployment, String dataset, String region, String environment) {
    String relativeReference = cname
      .replace("$(deployment)", deployment)
      .replace("$(dataset)", dataset)
      .replace("$(region)", region)
      .replace("$(env)", environment);

    return scheme + "://" + relativeReference;
  }

  public String getUriForLocation(String scheme, String location) {
    String base = target.replace("$(deployment)", deployment).replace("$(dataset)", dataset);
    boolean hasRegions = (regions != null && regions.size() > 0);
    boolean hasEnvironments = (environments != null && environments.size() > 0);

    if (!hasRegions && !hasEnvironments) {
      if (base.equals(location)) {
        return getUri(scheme, deployment, dataset, "", "");
      }
    } else if (!hasRegions && hasEnvironments) {
      for (String environment : environments) {
        String potential = base.replace("$(env)", environment);
        if (potential.equals(location)) {
          return getUri(scheme, deployment, dataset, "", environment);
        }
      }
    } else if (hasRegions && !hasEnvironments) {
      for (String region : regions) {
        String potential = base.replace("$(region)", region);
        if (potential.equals(location)) {
          return getUri(scheme, deployment, dataset, region, "");
        }
      }
    } else { // has both regions and environments
      for (String region : regions) {
        for (String environment : environments) {
          String potential = base.replace("$(region)", region).replace("$(env)", environment);
          if (potential.equals(location)) {
            return getUri(scheme, deployment, dataset, region, environment);
          }
        }
      }
    }

    return null;
  }
}
