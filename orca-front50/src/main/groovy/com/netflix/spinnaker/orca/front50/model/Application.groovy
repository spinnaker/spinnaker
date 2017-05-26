/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package com.netflix.spinnaker.orca.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@Canonical
class Application {
  public String name
  public String description
  public String email
  public String updateTs
  public String createTs
  public Boolean platformHealthOnly
  public Boolean platformHealthOnlyShowOverride

  @JsonIgnore
  String user
  @JsonIgnore
  List<String> requiredGroupMembership = new ArrayList<>()

  private Map<String, Object> details = new HashMap<String, Object>()

  @JsonSetter
  void setUser(String user) {
    this.user = user
  }

  @JsonSetter
  void setRequiredGroupMembership(List<String> requiredGroupMembership) {
    this.requiredGroupMembership = requiredGroupMembership
  }

  @JsonAnyGetter
  public Map<String,Object> details() {
    return details
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    details.put(name, value)
  }

  @JsonIgnore
  Permission getPermission() {
    return new Permission()
  }

  @EqualsAndHashCode(excludes = 'lastModified')
  @ToString
  class Permission {
    String name = Application.this.name
    Long lastModified = System.currentTimeMillis()
    String lastModifiedBy = Application.this.user ?: "unknown"
    List<String> requiredGroupMembership = Application.this.requiredGroupMembership
  }
}
