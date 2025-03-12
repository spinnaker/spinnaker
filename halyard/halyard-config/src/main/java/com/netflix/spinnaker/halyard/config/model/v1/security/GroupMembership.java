/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.config.model.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = false)
@Data
public class GroupMembership extends Node {

  private final String nodeName = "groupMembership";

  private RoleProviderType service = RoleProviderType.EXTERNAL;
  private GoogleRoleProvider google = new GoogleRoleProvider();
  private GithubRoleProvider github = new GithubRoleProvider();
  private FileRoleProvider file = new FileRoleProvider();
  private LdapRoleProvider ldap = new LdapRoleProvider();

  public static Class<? extends RoleProvider> translateRoleProviderType(String roleProvider) {
    Optional<? extends Class<?>> res =
        Arrays.stream(GroupMembership.class.getDeclaredFields())
            .filter(f -> f.getName().equals(roleProvider))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends RoleProvider>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No role provider with name \"" + roleProvider + "\" handled by halyard");
    }
  }

  public enum RoleProviderType {
    EXTERNAL(""),
    FILE("file"),
    GOOGLE("google"),
    GITHUB("github"),
    LDAP("ldap");

    @Getter final String id;

    RoleProviderType(String id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return id;
    }
  }
}
