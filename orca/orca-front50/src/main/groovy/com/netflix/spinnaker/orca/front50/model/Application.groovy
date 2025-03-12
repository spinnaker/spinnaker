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
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j

@Slf4j
@Canonical
class Application {
  public String name
  public String description
  public String email
  public String updateTs
  public String createTs
  public String cloudProviders // comma delimited list of cloud provider strings
  public Boolean platformHealthOnly
  public Boolean platformHealthOnlyShowOverride

  @JsonIgnore
  String user
  @JsonIgnore
  private Permissions permissions

  private Map<String, Object> details = new HashMap<String, Object>()

  @JsonSetter
  void setUser(String user) {
    this.user = user
  }

  @JsonSetter
  void setRequiredGroupMembership(List<String> requiredGroupMembership) {
    log.warn("Required group membership settings detected in application ${name}. " +
                 "Please update to `permissions` format.")

    if (permissions == null || !permissions.isRestricted()) { // Do not overwrite permissions if it contains values
      Permissions.Builder b = new Permissions.Builder()
      requiredGroupMembership.each {
        b.add(Authorization.READ, it.trim().toLowerCase())
        b.add(Authorization.WRITE, it.trim().toLowerCase())
      }
      permissions = b.build()
    }
  }

  @JsonSetter
  void setPermissions(Permissions permissions){
    this.permissions = permissions
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
    Permissions permissions = Application.this.permissions
  }
}
