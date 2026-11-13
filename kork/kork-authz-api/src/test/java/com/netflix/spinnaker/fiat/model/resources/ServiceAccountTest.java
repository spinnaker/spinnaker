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
 */

package com.netflix.spinnaker.fiat.model.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.fiat.model.UserPermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ServiceAccountTest {

  @Test
  public void shouldConvertToUserPermissionFilteringNonTextStrings() {
    // Setup
    ServiceAccount acct =
        new ServiceAccount()
            .setName("my-svc-acct")
            .setMemberOf(Arrays.asList("foo", "bar", "", "   "));

    // When
    UserPermission result = acct.toUserPermission();

    // Then
    assertEquals("my-svc-acct", result.getId());

    Set<Role> expectedRoles =
        new HashSet<>(
            Arrays.asList(
                new Role("foo").setSource(Role.Source.EXTERNAL),
                new Role("bar").setSource(Role.Source.EXTERNAL)));
    assertTrue(result.getRoles().containsAll(expectedRoles));
  }
}
