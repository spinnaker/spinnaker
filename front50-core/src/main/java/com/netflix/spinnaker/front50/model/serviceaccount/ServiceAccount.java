/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */
package com.netflix.spinnaker.front50.model.serviceaccount;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.ArrayList;
import java.util.List;

public class ServiceAccount implements Timestamped {

  private String name;
  private Long lastModified;
  private String lastModifiedBy;
  private List<String> memberOf = new ArrayList<>();

  @Override
  @JsonIgnore
  public String getId() {
    return name.toLowerCase();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getLastModified() {
    return lastModified;
  }

  public void setLastModified(Long lastModified) {
    this.lastModified = lastModified;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public List<String> getMemberOf() {
    return memberOf;
  }

  public void setMemberOf(List<String> memberOf) {
    this.memberOf = memberOf;
  }
}
