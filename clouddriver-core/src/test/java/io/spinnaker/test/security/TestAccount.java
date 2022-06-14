/*
 * Copyright 2021 Apple Inc.
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

package io.spinnaker.test.security;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonTypeName("test")
@NonnullByDefault
public class TestAccount implements AccessControlledAccountDefinition {
  private final Permissions.Builder permissions = new Permissions.Builder();
  private final Map<String, Object> data = new HashMap<>();

  @Override
  @JsonIgnore
  public String getName() {
    return (String) data.get("name");
  }

  public Permissions.Builder getPermissions() {
    return permissions;
  }

  @JsonAnyGetter
  public Map<String, Object> getData() {
    return data;
  }

  @JsonAnySetter
  public void setData(String key, Object value) {
    data.put(key, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TestAccount that = (TestAccount) o;
    return permissions.equals(that.permissions) && data.equals(that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(permissions, data);
  }
}
