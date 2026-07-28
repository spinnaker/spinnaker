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

package com.netflix.spinnaker.orca.front50.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orca-local wire model for a managed service account exchanged with Front50.
 *
 * <p>In the owner-local / token-carried model Front50 owns managed service accounts (it persists
 * them and resolves their roles), so Orca only needs the minimal {@code name} + {@code memberOf}
 * fields it serializes to Front50's {@code POST /serviceAccounts} and reads back from {@code GET
 * /serviceAccounts/{id}}. The kork-authz {@link com.netflix.spinnaker.security.authz.Role} model
 * carries no resource/view machinery, so this thin POJO is the Orca-side equivalent.
 */
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ServiceAccount)) {
      return false;
    }
    ServiceAccount that = (ServiceAccount) o;
    return Objects.equals(name, that.name) && Objects.equals(memberOf, that.memberOf);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, memberOf);
  }

  @Override
  public String toString() {
    return "ServiceAccount{name='" + name + "', memberOf=" + memberOf + '}';
  }
}
