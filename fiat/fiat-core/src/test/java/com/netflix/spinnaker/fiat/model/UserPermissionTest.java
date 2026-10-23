/*
 * Copyright 2022 Apple, Inc.
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
 */

package com.netflix.spinnaker.fiat.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.model.resources.Authorizable;
import com.netflix.spinnaker.fiat.model.resources.BaseAccessControlled;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.Viewable;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

class UserPermissionTest {
  @Test
  void rolesConvertToGrantedAuthorities() {
    Role first = new Role("first");
    Role second = new Role("second");
    Role third = new Role("third");
    UserPermission userPermission =
        new UserPermission().setId("jesse").setRoles(Set.of(first, second, third));
    Set<GrantedAuthority> authorities = userPermission.getView().toGrantedAuthorities();
    Set<String> result = AuthorityUtils.authorityListToSet(authorities);
    assertThat(result).containsExactlyInAnyOrder("ROLE_first", "ROLE_second", "ROLE_third");
  }

  @Test
  void adminRoleConvertsToGrantedAuthority() {
    UserPermission userPermission = new UserPermission().setId("sherry").setAdmin(true);
    Set<GrantedAuthority> authorities = userPermission.getView().toGrantedAuthorities();
    assertThat(authorities).containsExactly(SpinnakerAuthorities.ADMIN_AUTHORITY);
  }

  @Test
  void accountManagerConvertsToGrantedAuthority() {
    UserPermission userPermission = new UserPermission().setId("ralph").setAccountManager(true);
    Set<GrantedAuthority> authorities = userPermission.getView().toGrantedAuthorities();
    assertThat(authorities).containsExactly(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
  }

  /**
   * A permission resource of a type Fiat doesn't model itself, like one a plugin would register.
   * Stands in for the real extension resource (artifact accounts) which doesn't exist on this
   * release.
   */
  static class TestExtensionResource extends BaseAccessControlled<TestExtensionResource>
      implements Viewable {
    static final ResourceType TYPE = new ResourceType("test_extension_resource");

    private String name;
    private Permissions permissions = Permissions.EMPTY;

    @Override
    public String getName() {
      return name;
    }

    public TestExtensionResource setName(String name) {
      this.name = name;
      return this;
    }

    @Override
    public ResourceType getResourceType() {
      return TYPE;
    }

    @Override
    public Permissions getPermissions() {
      return permissions;
    }

    @Override
    public TestExtensionResource setPermissions(Permissions permissions) {
      this.permissions = permissions;
      return this;
    }

    @Override
    public View getView(Set<Role> userRoles, boolean isAdmin) {
      return new View(this, userRoles);
    }

    public static class View extends BaseView implements Authorizable {
      private final String name;
      private final Set<Authorization> authorizations;

      View(TestExtensionResource resource, Set<Role> userRoles) {
        this.name = resource.name;
        this.authorizations = resource.permissions.getAuthorizations(userRoles);
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Set<Authorization> getAuthorizations() {
        return authorizations;
      }
    }
  }

  @Test
  void extensionResourcesSurviveJsonRoundTrip() throws Exception {
    TestExtensionResource resource =
        new TestExtensionResource()
            .setName("my-resource")
            .setPermissions(new Permissions.Builder().add(Authorization.READ, "first").build());
    UserPermission userPermission =
        new UserPermission()
            .setId("jesse")
            .setRoles(Set.of(new Role("first")))
            .addResources(Set.of(resource));

    // Same settings as the mapper behind FiatService.
    ObjectMapper mapper =
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    String json = mapper.writeValueAsString(userPermission.getView());
    UserPermission.View view = mapper.readValue(json, UserPermission.View.class);

    Set<Authorizable> extensionResources =
        view.getExtensionResources().get(TestExtensionResource.TYPE);
    assertThat(extensionResources)
        .singleElement()
        .satisfies(
            authorizable -> {
              assertThat(authorizable.getName()).isEqualTo("my-resource");
              assertThat(authorizable.getAuthorizations()).containsExactly(Authorization.READ);
            });
  }
}
