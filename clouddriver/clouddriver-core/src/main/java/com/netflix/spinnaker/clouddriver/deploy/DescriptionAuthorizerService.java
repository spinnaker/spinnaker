package com.netflix.spinnaker.clouddriver.deploy;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import io.micrometer.core.instrument.MeterRegistry;
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

  private static final String SKIP_AUTHORIZATION_METRIC_NAME = "authorization.skipped";
  private static final String MISSING_APPLICATION_METRIC_NAME = "authorization.missingApplication";
  private static final String AUTHORIZATION_METRIC_NAME = "authorization";

  private final MeterRegistry registry;
  private final FiatPermissionEvaluator fiatPermissionEvaluator;
  private final SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps;
  private final AccountDefinitionSecretManager secretManager;

  public DescriptionAuthorizerService(
      MeterRegistry registry,
      Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
      SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps,
      AccountDefinitionSecretManager secretManager) {
    this.registry = registry;
    this.fiatPermissionEvaluator = fiatPermissionEvaluator.orElse(null);
    this.opsSecurityConfigProps = opsSecurityConfigProps;
    this.secretManager = secretManager;
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
                SKIP_AUTHORIZATION_METRIC_NAME,
                "descriptionClass",
                description.getClass().getSimpleName())
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
        && !secretManager.canAccessAccountWithSecrets(auth.getName(), account)) {
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
              MISSING_APPLICATION_METRIC_NAME,
              "descriptionClass",
              description.getClass().getSimpleName(),
              "hasValidationErrors",
              String.valueOf(errors.hasErrors()))
          .increment();

      log.warn(
          "No application(s) specified for operation with account restriction (type: {}, account: {}, hasValidationErrors: {})",
          description.getClass().getSimpleName(),
          account,
          errors.hasErrors());
    }

    registry
        .counter(
            AUTHORIZATION_METRIC_NAME,
            "descriptionClass",
            description.getClass().getSimpleName(),
            "success",
            String.valueOf(hasPermission))
        .increment();
  }
}
