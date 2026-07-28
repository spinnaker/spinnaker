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

import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * On every startup, upserts each entry declared under {@code service-accounts} into the
 * service-account store. The config is authoritative: if an account already exists its {@code
 * memberOf} list is overwritten to match the config value.
 *
 * <p>Only accounts declared here are eligible for service-account API token minting (Gate enforces
 * this when proxying token-creation requests to Front50).
 */
@Component
@EnableConfigurationProperties(ServiceAccountsProperties.class)
public class ServiceAccountsInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ServiceAccountsInitializer.class);

  private final ServiceAccountsProperties properties;
  private final ServiceAccountDAO serviceAccountDAO;

  public ServiceAccountsInitializer(
      ServiceAccountsProperties properties, ServiceAccountDAO serviceAccountDAO) {
    this.properties = properties;
    this.serviceAccountDAO = serviceAccountDAO;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (properties.getServiceAccounts().isEmpty()) {
      return;
    }

    Collection<ServiceAccount> existing = serviceAccountDAO.all();
    Set<String> existingNames =
        existing.stream().map(ServiceAccount::getName).collect(Collectors.toSet());

    for (ServiceAccountsProperties.ServiceAccountDefinition def : properties.getServiceAccounts()) {
      if (def.getName() == null || def.getName().isBlank()) {
        log.warn("Skipping service-account entry with null/blank name");
        continue;
      }

      ServiceAccount sa = new ServiceAccount();
      sa.setName(def.getName());
      sa.setMemberOf(def.getMemberOf());

      if (existingNames.contains(def.getName())) {
        log.info("Updating service-account '{}' memberOf={}", def.getName(), def.getMemberOf());
        serviceAccountDAO.update(def.getName(), sa);
      } else {
        log.info("Creating service-account '{}' memberOf={}", def.getName(), def.getMemberOf());
        serviceAccountDAO.create(def.getName(), sa);
      }
    }
  }
}
