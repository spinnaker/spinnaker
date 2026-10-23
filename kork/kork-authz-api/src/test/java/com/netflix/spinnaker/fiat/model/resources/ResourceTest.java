/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.fiat.model.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ResourceTest {

  @Test
  public void shouldParseResourceTypeFromRedisKey() {
    assertEquals(ResourceType.ACCOUNT, ResourceType.parse(":accounts"));
    assertEquals(ResourceType.ACCOUNT, ResourceType.parse("abc:accounts"));
    assertEquals(ResourceType.ACCOUNT, ResourceType.parse("abc:def:accounts"));
    assertEquals(ResourceType.APPLICATION, ResourceType.parse(":applications"));
    assertEquals(ResourceType.ACCOUNT, ResourceType.parse("account"));
    assertEquals(ResourceType.ACCOUNT, ResourceType.parse("accounts"));
    assertEquals(ResourceType.ACCOUNT, ResourceType.parse("aCCoUnTs"));
  }

  @Test
  public void shouldThrowExceptionOnInvalidParseInput() {
    assertThrows(NullPointerException.class, () -> ResourceType.parse(null));
    assertThrows(IllegalArgumentException.class, () -> ResourceType.parse(""));
    assertThrows(IllegalArgumentException.class, () -> ResourceType.parse("account:"));
    assertThrows(IllegalArgumentException.class, () -> ResourceType.parse("account:s"));
  }

  @Test
  public void shouldComputeAuthorizationsCorrectly() {
    Permissions.Builder b = new Permissions.Builder().add(Authorization.READ, "role1");
    Permissions p = b.build();

    Set<Role> roles = new HashSet<>();
    roles.add(new Role("role1"));
    roles.add(new Role("role2"));

    assertTrue(p.getAuthorizations(new HashSet<Role>()).isEmpty());
    assertEquals(Set.of(Authorization.READ), p.getAuthorizations(Set.of(new Role("role1"))));
    assertEquals(Set.of(Authorization.READ), p.getAuthorizations(roles));

    b.add(Authorization.WRITE, "role2");
    p = b.build();

    assertEquals(Set.of(Authorization.READ, Authorization.WRITE), p.getAuthorizations(roles));
  }

  @Test
  public void shouldDetectWhenRestricted() {
    Permissions.Builder b = new Permissions.Builder();
    Permissions p = b.build();

    assertFalse(p.isRestricted());

    b.add(Authorization.READ, "role1");
    p = b.build();

    assertTrue(p.isRestricted());
  }
}
