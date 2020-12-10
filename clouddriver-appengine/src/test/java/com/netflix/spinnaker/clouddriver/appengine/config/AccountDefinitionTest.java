/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.appengine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class AccountDefinitionTest {

  @Test
  public void testCredentialsEquality() {
    AppengineConfigurationProperties.ManagedAccount account1 =
        new AppengineConfigurationProperties.ManagedAccount()
            .setServiceAccountEmail("email@example.com")
            .setServices(List.of("a"));
    account1.setName("appengine-1");
    AppengineConfigurationProperties.ManagedAccount account2 =
        new AppengineConfigurationProperties.ManagedAccount()
            .setServiceAccountEmail("email@example.com")
            .setServices(List.of("a"));
    account2.setName("appengine-2");

    assertThat(account1).isNotEqualTo(account2);

    // Check name is part of the comparison
    account2.setName("appengine-1");
    assertThat(account1).isEqualTo(account1);

    // Check computedServiceAccount is not
    account2.setComputedServiceAccountEmail("other@example.com");
    assertThat(account1).isEqualTo(account1);

    // Check that git password is in the same
    account2.setServices(List.of("b"));
    assertThat(account1).isNotEqualTo(account2);
  }
}
