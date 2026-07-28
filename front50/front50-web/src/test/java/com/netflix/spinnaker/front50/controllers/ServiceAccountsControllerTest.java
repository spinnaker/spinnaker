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
package com.netflix.spinnaker.front50.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.netflix.spinnaker.front50.ServiceAccountsService;
import com.netflix.spinnaker.front50.config.ServiceAccountsProperties;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceAccountsControllerTest {

  @Mock ServiceAccountsService serviceAccountsService;

  ServiceAccountsProperties properties;
  ServiceAccountsController controller;

  @BeforeEach
  void setUp() {
    properties = new ServiceAccountsProperties();
    controller = new ServiceAccountsController(serviceAccountsService, properties);
  }

  private ServiceAccountsProperties.ServiceAccountDefinition def(String name, String... roles) {
    ServiceAccountsProperties.ServiceAccountDefinition d =
        new ServiceAccountsProperties.ServiceAccountDefinition();
    d.setName(name);
    d.setMemberOf(List.of(roles));
    return d;
  }

  @Test
  @DisplayName("returns a service account for each configured entry")
  void returnsConfiguredServiceAccounts() {
    properties.setServiceAccounts(
        List.of(def("ci-bot", "ops", "deploy"), def("release-bot", "release")));

    Set<ServiceAccount> result = controller.getTokenEligibleServiceAccounts();

    assertThat(result)
        .extracting(ServiceAccount::getName)
        .containsExactlyInAnyOrder("ci-bot", "release-bot");
    assertThat(result)
        .filteredOn(sa -> sa.getName().equals("ci-bot"))
        .singleElement()
        .satisfies(sa -> assertThat(sa.getMemberOf()).containsExactly("ops", "deploy"));
  }

  @Test
  @DisplayName("empty config returns empty result")
  void emptyConfigReturnsEmpty() {
    properties.setServiceAccounts(List.of());

    assertThat(controller.getTokenEligibleServiceAccounts()).isEmpty();
  }

  @Test
  @DisplayName("entries with null or blank names are skipped")
  void skipsNullAndBlankNames() {
    ServiceAccountsProperties.ServiceAccountDefinition nullName =
        new ServiceAccountsProperties.ServiceAccountDefinition();
    nullName.setName(null);
    ServiceAccountsProperties.ServiceAccountDefinition blankName =
        new ServiceAccountsProperties.ServiceAccountDefinition();
    blankName.setName("   ");
    properties.setServiceAccounts(List.of(nullName, blankName, def("real-bot")));

    Set<ServiceAccount> result = controller.getTokenEligibleServiceAccounts();

    assertThat(result).extracting(ServiceAccount::getName).containsExactly("real-bot");
  }

  @Test
  @DisplayName("token-eligible endpoint does not consult the service-account store")
  void doesNotReadFromStore() {
    properties.setServiceAccounts(List.of(def("ci-bot", "ops")));

    controller.getTokenEligibleServiceAccounts();

    verifyNoInteractions(serviceAccountsService);
  }
}
