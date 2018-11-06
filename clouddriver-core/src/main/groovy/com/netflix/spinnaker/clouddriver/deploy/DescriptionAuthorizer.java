/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.deploy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class DescriptionAuthorizer<T> {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Registry registry;
  private final ObjectMapper objectMapper;
  private final FiatPermissionEvaluator fiatPermissionEvaluator;

  private final Id missingApplicationId;
  private final Id authorizationId;

  public DescriptionAuthorizer(Registry registry,
                               ObjectMapper objectMapper,
                               Optional<FiatPermissionEvaluator> fiatPermissionEvaluator) {
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.fiatPermissionEvaluator = fiatPermissionEvaluator.orElse(null);

    this.missingApplicationId = registry.createId("authorization.missingApplication");
    this.authorizationId = registry.createId("authorization");
  }

  public void authorize(T description, Errors errors) {
    if (fiatPermissionEvaluator == null || description == null) {
      return;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    String account = null;
    List<String> applications = new ArrayList<>();

    if (description instanceof AccountNameable) {
      AccountNameable accountNameable = (AccountNameable) description;
      account = accountNameable.getAccount();
    }

    if (description instanceof ApplicationNameable) {
      ApplicationNameable applicationNameable = (ApplicationNameable) description;
      applications.addAll(
        Optional.ofNullable(applicationNameable.getApplications()).orElse(Collections.emptyList())
          .stream()
          .filter(Objects::nonNull)
          .collect(Collectors.toList())
      );
    }

    if (description instanceof ResourcesNameable) {
      ResourcesNameable resourcesNameable = (ResourcesNameable) description;
      applications.addAll(
        Optional.ofNullable(resourcesNameable.getResourceApplications()).orElse(Collections.emptyList())
          .stream()
          .filter(Objects::nonNull)
          .collect(Collectors.toList())
      );
    }


    boolean hasPermission = true;
    if (account != null && !fiatPermissionEvaluator.hasPermission(auth, account, "ACCOUNT", "WRITE")) {
      hasPermission = false;
      errors.reject("authorization", format("Access denied to account %s", account));
    }

    if (!applications.isEmpty()) {
      fiatPermissionEvaluator.storeWholePermission();

      for (String application: applications) {
        if (!fiatPermissionEvaluator.hasPermission(auth, application, "APPLICATION", "WRITE")) {
          hasPermission = false;
          errors.reject("authorization", format("Access denied to application %s", application));
        }
      }
    }


    if (account != null && applications.isEmpty()) {
      registry.counter(
          missingApplicationId
            .withTag("descriptionClass", description.getClass().getSimpleName())
        ).increment();

      String rawJson = null;
      try {
        rawJson = objectMapper.writeValueAsString(description);
      } catch (JsonProcessingException ignored) {}

      log.warn(
        "No application(s) specified for operation with account restriction (type: {}, rawJson: {})",
        description.getClass().getSimpleName(),
        rawJson
      );
    }

    registry.counter(
      authorizationId
        .withTag("descriptionClass", description.getClass().getSimpleName())
        .withTag("success", hasPermission)
    ).increment();
  }
}
