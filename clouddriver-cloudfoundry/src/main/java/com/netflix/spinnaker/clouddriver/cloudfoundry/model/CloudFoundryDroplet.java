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
import com.netflix.spinnaker.clouddriver.model.Image;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Collection;

@RequiredArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudFoundryDroplet implements Image {
  String id;
  String name;
  CloudFoundrySpace space;
  String stack;
  Collection<CloudFoundryBuildpack> buildpacks;
  CloudFoundryPackage sourcePackage;
  String packageChecksum;

  @Override
  public String getRegion() {
    return space != null ? space.getRegion() : null;
  }
}
