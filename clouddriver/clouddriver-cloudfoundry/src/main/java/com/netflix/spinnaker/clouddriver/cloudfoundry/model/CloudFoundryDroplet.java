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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.model.Image;
import java.util.Collection;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(of = "id")
@Builder
@JsonDeserialize(builder = CloudFoundryDroplet.CloudFoundryDropletBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudFoundryDroplet implements Image {
  @JsonView(Views.Cache.class)
  String id;

  @JsonView(Views.Cache.class)
  String name;

  @JsonView(Views.Cache.class)
  String stack;

  @JsonView(Views.Cache.class)
  Collection<CloudFoundryBuildpack> buildpacks;

  @JsonView(Views.Cache.class)
  @Nullable
  CloudFoundrySpace space;

  @JsonView(Views.Cache.class)
  @Nullable
  CloudFoundryPackage sourcePackage;

  @Override
  public String getRegion() {
    return space != null ? space.getRegion() : null;
  }
}
