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

class Application {
  String name
  String description
  String email
  String accounts
  String updateTs
  String createTs

  private Map<String, Object> details = new HashMap<String, Object>()

  @JsonAnyGetter
  public Map<String,Object> details() {
    return details;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    details.put(name, value);
  }

  @JsonIgnore
  Set<String> listAccounts() {
    if (!accounts?.trim()) {
      return []
    }
    return accounts.split(",").collect { it.toLowerCase() } as Set<String>
  }

  @JsonIgnore
  void updateAccounts(Set<String> accounts) {
    this.accounts = accounts ? accounts.collect { it.trim().toLowerCase() }.join(",") : null
  }
}
