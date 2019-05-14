/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.model.Application;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@EqualsAndHashCode(of = "name")
@Builder
@JsonDeserialize(builder = CloudFoundryApplication.CloudFoundryApplicationBuilder.class)
@JsonIgnoreProperties("clusters")
public class CloudFoundryApplication implements Application {
  @JsonView(Views.Cache.class)
  String name;

  @Wither
  @JsonView(Views.Relationship.class)
  Set<CloudFoundryCluster> clusters;

  @Override
  public Map<String, Set<String>> getClusterNames() {
    return clusters == null
        ? emptyMap()
        : clusters.stream()
            .collect(
                groupingBy(
                    CloudFoundryCluster::getAccountName,
                    mapping(CloudFoundryCluster::getName, toSet())));
  }

  @Override
  public Map<String, String> getAttributes() {
    return emptyMap();
  }
}
