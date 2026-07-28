/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.services.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal local view of a Front50 managed service account, deserialized from {@code GET
 * front50/serviceAccounts}. Gate resolves service-account membership locally (owner-local /
 * token-carried authorization) instead of via a remote round-trip.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceAccount {
  private String name;
  private List<String> memberOf = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getMemberOf() {
    return memberOf;
  }

  public void setMemberOf(List<String> memberOf) {
    this.memberOf = memberOf == null ? new ArrayList<>() : memberOf;
  }
}
