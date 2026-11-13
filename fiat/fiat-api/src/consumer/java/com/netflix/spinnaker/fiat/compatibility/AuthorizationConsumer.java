/*
 * Copyright 2026 Spinnaker Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.fiat.compatibility;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/** Compiled independently of Fiat's main and test outputs, with each dependency pair. */
public class AuthorizationConsumer {
  public static void main(String[] args) throws IOException {
    UserPermission permission =
        new UserPermission()
            .setId("consumer")
            .setRoles(Set.of(new Role("reader")))
            .setApplications(
                Set.of(
                    new Application()
                        .setName("application")
                        .setPermissions(
                            new Permissions.Builder().add(Authorization.READ, "reader").build())));
    if (!permission
        .getView()
        .getApplications()
        .iterator()
        .next()
        .getAuthorizations()
        .contains(Authorization.READ)) {
      throw new AssertionError("Authorization model did not preserve role permissions");
    }
    FiatClientConfigurationProperties configuration =
        new FiatClientConfigurationProperties().setEnabled(true).setBaseUrl("http://localhost/");
    if (!configuration.isEnabled()) {
      throw new AssertionError("Fiat configuration API is unavailable");
    }
    verifySingleDefinition();
  }

  private static void verifySingleDefinition() throws IOException {
    for (Class<?> type :
        Set.of(
            UserPermission.class,
            FiatService.class,
            FiatPermissionEvaluator.class,
            EnableFiatAutoConfig.class)) {
      String resource = type.getName().replace('.', '/') + ".class";
      if (Collections.list(type.getClassLoader().getResources(resource)).size() != 1) {
        throw new AssertionError("Expected exactly one definition of " + type.getName());
      }
    }
  }
}
