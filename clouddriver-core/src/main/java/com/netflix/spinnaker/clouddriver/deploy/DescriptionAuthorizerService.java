package com.netflix.spinnaker.clouddriver.deploy;

import static java.lang.String.format;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.Errors;

public class DescriptionAuthorizerService {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Registry registry;
  private final FiatPermissionEvaluator fiatPermissionEvaluator;
  private final SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps;

  private final Id skipAuthorizationId;
  private final Id missingApplicationId;
  private final Id authorizationId;

  public DescriptionAuthorizerService(
      Registry registry,
      Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
      SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps) {
    this.registry = registry;
    this.fiatPermissionEvaluator = fiatPermissionEvaluator.orElse(null);
    this.opsSecurityConfigProps = opsSecurityConfigProps;

    this.skipAuthorizationId = registry.createId("authorization.skipped");
    this.missingApplicationId = registry.createId("authorization.missingApplication");
    this.authorizationId = registry.createId("authorization");
  }

  public void authorize(Object description, Errors errors) {
    authorize(description, errors, List.of(ResourceType.ACCOUNT, ResourceType.APPLICATION));
  }

  public void authorize(Object description, Errors errors, Collection<ResourceType> resourceTypes) {
    if (fiatPermissionEvaluator == null || description == null) {
      return;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    String account = null;
    List<String> applications = new ArrayList<>();
    boolean requiresApplicationRestriction = true;

    if (description instanceof AccountNameable) {
      AccountNameable accountNameable = (AccountNameable) description;

      requiresApplicationRestriction = accountNameable.requiresApplicationRestriction();

      if (!accountNameable.requiresAuthorization(opsSecurityConfigProps)) {
        registry
            .counter(
                skipAuthorizationId.withTag(
                    "descriptionClass", description.getClass().getSimpleName()))
            .increment();

        log.info(
            "Skipping authorization for operation `{}` in account `{}`.",
            description.getClass().getSimpleName(),
            accountNameable.getAccount());
      } else {
        account = accountNameable.getAccount();
      }
    }

    if (description instanceof ApplicationNameable) {
      ApplicationNameable applicationNameable = (ApplicationNameable) description;
      applications.addAll(
          Optional.ofNullable(applicationNameable.getApplications())
              .orElse(Collections.emptyList())
              .stream()
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
    }

    if (description instanceof ResourcesNameable) {
      ResourcesNameable resourcesNameable = (ResourcesNameable) description;

      applications.addAll(
          Optional.ofNullable(resourcesNameable.getResourceApplications())
              .orElse(Collections.emptyList())
              .stream()
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
    }

    boolean hasPermission = true;
    if (resourceTypes.contains(ResourceType.ACCOUNT)
        && account != null
        && !fiatPermissionEvaluator.hasPermission(auth, account, "ACCOUNT", "WRITE")) {
      hasPermission = false;
      errors.reject("authorization.account", format("Access denied to account %s", account));
    }

    if (resourceTypes.contains(ResourceType.APPLICATION) && !applications.isEmpty()) {
      fiatPermissionEvaluator.storeWholePermission();

      for (String application : applications) {
        if (!fiatPermissionEvaluator.hasPermission(auth, application, "APPLICATION", "WRITE")) {
          hasPermission = false;
          errors.reject(
              "authorization.application", format("Access denied to application %s", application));
        }
      }
    }

    if (requiresApplicationRestriction && account != null && applications.isEmpty()) {
      registry
          .counter(
              missingApplicationId
                  .withTag("descriptionClass", description.getClass().getSimpleName())
                  .withTag("hasValidationErrors", errors.hasErrors()))
          .increment();

      log.warn(
          "No application(s) specified for operation with account restriction (type: {}, account: {}, hasValidationErrors: {})",
          description.getClass().getSimpleName(),
          account,
          errors.hasErrors());
    }

    registry
        .counter(
            authorizationId
                .withTag("descriptionClass", description.getClass().getSimpleName())
                .withTag("success", hasPermission))
        .increment();
  }
}
