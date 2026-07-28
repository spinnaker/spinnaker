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

package com.netflix.spinnaker.front50.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ServiceAccountsInitializerTest {

  @Mock ServiceAccountDAO serviceAccountDAO;

  ServiceAccountsProperties properties;
  ServiceAccountsInitializer initializer;

  @BeforeEach
  void setUp() {
    properties = new ServiceAccountsProperties();
  }

  private DefaultApplicationArguments noArgs() {
    return new DefaultApplicationArguments();
  }

  private ServiceAccountsProperties.ServiceAccountDefinition def(String name, String... roles) {
    ServiceAccountsProperties.ServiceAccountDefinition d =
        new ServiceAccountsProperties.ServiceAccountDefinition();
    d.setName(name);
    d.setMemberOf(List.of(roles));
    return d;
  }

  // ---------------------------------------------------------------------------
  // Creation
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("creates a new service account when it does not yet exist")
  void createsNewServiceAccount() throws Exception {
    properties.setServiceAccounts(List.of(def("ci-pipeline-bot", "ops", "deploy")));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of());

    initializer.run(noArgs());

    ArgumentCaptor<ServiceAccount> captor = ArgumentCaptor.forClass(ServiceAccount.class);
    verify(serviceAccountDAO).create(eq("ci-pipeline-bot"), captor.capture());
    ServiceAccount created = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(created.getName()).isEqualTo("ci-pipeline-bot");
    org.assertj.core.api.Assertions.assertThat(created.getMemberOf())
        .containsExactly("ops", "deploy");
  }

  @Test
  @DisplayName("creates multiple new service accounts")
  void createsMultipleNewAccounts() throws Exception {
    properties.setServiceAccounts(List.of(def("bot-a", "role-a"), def("bot-b", "role-b")));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of());

    initializer.run(noArgs());

    verify(serviceAccountDAO).create(eq("bot-a"), any());
    verify(serviceAccountDAO).create(eq("bot-b"), any());
    verify(serviceAccountDAO, never()).update(any(), any());
  }

  // ---------------------------------------------------------------------------
  // Upsert (update existing)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("updates memberOf when the service account already exists")
  void updatesExistingServiceAccount() throws Exception {
    ServiceAccount existing = new ServiceAccount();
    existing.setName("ci-pipeline-bot");
    existing.setMemberOf(List.of("old-role"));

    properties.setServiceAccounts(List.of(def("ci-pipeline-bot", "new-role-a", "new-role-b")));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of(existing));

    initializer.run(noArgs());

    ArgumentCaptor<ServiceAccount> captor = ArgumentCaptor.forClass(ServiceAccount.class);
    verify(serviceAccountDAO).update(eq("ci-pipeline-bot"), captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getMemberOf())
        .containsExactly("new-role-a", "new-role-b");
    verify(serviceAccountDAO, never()).create(any(), any());
  }

  @Test
  @DisplayName("creates new and updates existing in the same run")
  void createsAndUpdatesInSameRun() throws Exception {
    ServiceAccount existing = new ServiceAccount();
    existing.setName("existing-bot");
    existing.setMemberOf(List.of("old-role"));

    properties.setServiceAccounts(
        List.of(def("existing-bot", "new-role"), def("new-bot", "role-x")));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of(existing));

    initializer.run(noArgs());

    verify(serviceAccountDAO).update(eq("existing-bot"), any());
    verify(serviceAccountDAO).create(eq("new-bot"), any());
  }

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("empty config — no DAO calls are made")
  void emptyConfigDoesNothing() throws Exception {
    properties.setServiceAccounts(List.of());
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);

    initializer.run(noArgs());

    verifyNoInteractions(serviceAccountDAO);
  }

  @Test
  @DisplayName("entry with null name is skipped")
  void nullNameIsSkipped() throws Exception {
    ServiceAccountsProperties.ServiceAccountDefinition bad =
        new ServiceAccountsProperties.ServiceAccountDefinition();
    bad.setName(null);
    properties.setServiceAccounts(List.of(bad));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of());

    initializer.run(noArgs());

    verify(serviceAccountDAO, never()).create(any(), any());
    verify(serviceAccountDAO, never()).update(any(), any());
  }

  @Test
  @DisplayName("entry with blank name is skipped")
  void blankNameIsSkipped() throws Exception {
    ServiceAccountsProperties.ServiceAccountDefinition bad =
        new ServiceAccountsProperties.ServiceAccountDefinition();
    bad.setName("   ");
    properties.setServiceAccounts(List.of(bad));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of());

    initializer.run(noArgs());

    verify(serviceAccountDAO, never()).create(any(), any());
    verify(serviceAccountDAO, never()).update(any(), any());
  }

  @Test
  @DisplayName("service account with no memberOf roles is created with empty list")
  void noRolesCreatedWithEmptyList() throws Exception {
    properties.setServiceAccounts(List.of(def("bare-bot")));
    initializer = new ServiceAccountsInitializer(properties, serviceAccountDAO);
    when(serviceAccountDAO.all()).thenReturn(List.of());

    initializer.run(noArgs());

    ArgumentCaptor<ServiceAccount> captor = ArgumentCaptor.forClass(ServiceAccount.class);
    verify(serviceAccountDAO).create(eq("bare-bot"), captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getMemberOf()).isEmpty();
  }
}
