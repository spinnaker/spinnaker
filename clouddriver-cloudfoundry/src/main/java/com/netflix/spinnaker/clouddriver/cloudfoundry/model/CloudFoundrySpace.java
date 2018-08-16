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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@ToString
@Getter
public class CloudFoundrySpace {
  private final String id;
  private final String name;
  CloudFoundryOrganization organization;

  public static CloudFoundrySpace fromRegion(String region) {
    String[] parts = region.split(">");
    String orgName = parts[0].trim();
    String spaceName = parts[1].trim();
    return new CloudFoundrySpace(null, spaceName, new CloudFoundryOrganization(null, orgName));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CloudFoundrySpace that = (CloudFoundrySpace) o;

    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    return organization != null ? organization.getName().equals(that.organization.getName()) :
      that.organization == null;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (organization.getName() != null ? organization.getName().hashCode() : 0);
    return result;
  }

  @JsonIgnore
  String getRegion() {
    return organization.getName() + " > " + name;
  }
}
