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

package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pipelinetriggers.QuietPeriodIndicator;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService.TriggerResponse;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Triggers a {@link Pipeline} by invoking _Orca_. */
@Component
@Slf4j
public class PipelineInitiator {

  private final Registry registry;
  private final DynamicConfigService dynamicConfigService;
  private final OrcaService orca;
  private final FiatPermissionEvaluator fiatPermissionEvaluator;
  private final FiatStatus fiatStatus;

  private final ObjectMapper objectMapper;
  private final QuietPeriodIndicator quietPeriodIndicator;
  private final int retryCount;
  private final long retryDelayMillis;
  private final ExecutorService executorService;
  private final DiscoveryStatusListener discoveryStatusListener;

  @Autowired
  public PipelineInitiator(
      @NonNull Registry registry,
      @NonNull OrcaService orca,
      @NonNull Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
      @NonNull FiatStatus fiatStatus,
      @NonNull ExecutorService executorService,
      ObjectMapper objectMapper,
      @NonNull QuietPeriodIndicator quietPeriodIndicator,
      @NonNull DynamicConfigService dynamicConfigService,
      @NonNull DiscoveryStatusListener discoveryStatusListener,
      @Value("${orca.pipeline-initiator-retry-count:5}") int retryCount,
      @Value("${orca.pipeline-initiator-retry-delay-millis:5000}") long retryDelayMillis) {
    this.registry = registry;
    this.orca = orca;
    this.fiatPermissionEvaluator = fiatPermissionEvaluator.orElse(null);
    this.fiatStatus = fiatStatus;
    this.objectMapper = objectMapper;
    this.quietPeriodIndicator = quietPeriodIndicator;
    this.dynamicConfigService = dynamicConfigService;
    this.retryCount = retryCount;
    this.retryDelayMillis = retryDelayMillis;
    this.executorService = executorService;
    this.discoveryStatusListener = discoveryStatusListener;
  }

  @PostConstruct
  public void initialize() {
    if (!isEnabled(TriggerSource.EXTERNAL_EVENT)) {
      log.warn("Orca triggering is disabled");
    }
  }

  public enum TriggerSource {
    /** Triggered by CRON scheduler */
    CRON_SCHEDULER,

    /** Triggered by compensation job scheduler (aka missed scheduler) */
    COMPENSATION_SCHEDULER,

    /** Triggered by external event (manual, igor, etc) */
    EXTERNAL_EVENT
  }

  public void recordPipelineFailure(Pipeline pipeline) {
    Retrofit2SyncCall.execute(orca.recordFailure(pipeline));
  }

  public void startPipeline(Pipeline pipeline, TriggerSource triggerSource) {
    if (isEnabled(triggerSource)) {
      try {
        long now = System.currentTimeMillis();
        boolean inQuietPeriod = quietPeriodIndicator.inQuietPeriod(now);
        boolean shouldTrigger = true;

        if (inQuietPeriod) {
          if (!pipeline.isRespectQuietPeriod()) {
            log.info(
                "Currently in quiet period but pipeline {} for app {} doesn't respect it, will trigger anyway",
                pipeline.getName(),
                pipeline.getApplication());
          } else {
            if (pipeline.getTrigger() != null) {
              if (quietPeriodIndicator.inQuietPeriod(now, pipeline.getTrigger().getType())) {
                log.info(
                    "Currently in quiet period and pipeline {} for app {} with trigger {} respects it - will not trigger it",
                    pipeline.getName(),
                    pipeline.getApplication(),
                    pipeline.getTrigger());

                shouldTrigger = false;
              } else {
                log.info(
                    "Currently in quiet period but pipeline trigger {} for pipeline {} for app {} is not one of suppressed trigger types, will trigger anyway",
                    pipeline.getTrigger().getType(),
                    pipeline.getName(),
                    pipeline.getApplication());
              }
            } else {
              log.info(
                  "Currently in quiet period but pipeline trigger is NULL for pipeline {} for app {}, will trigger anyway",
                  pipeline.getName(),
                  pipeline.getApplication());
            }
          }
        }

        if (shouldTrigger) {
          log.info("Triggering {} due to {}", pipeline, pipeline.getTrigger());

          final String templatedPipelineType = "templatedPipeline";
          if (templatedPipelineType.equals(pipeline.getType())) { // TODO(jacobkiefer): Constantize.
            // We need to store and re-set the propagateAuth flag, as it is ignored on
            // deserialization
            // TODO(ezimanyi): Find a better way to pass the propagateAuth flag than on the trigger
            // itself
            boolean propagateAuth =
                pipeline.getTrigger() != null && pipeline.getTrigger().isPropagateAuth();
            log.debug("Planning templated pipeline {} before triggering", pipeline);
            pipeline = pipeline.withPlan(true);

            try {
              Map pipelineToPlan = objectMapper.convertValue(pipeline, Map.class);
              Map resolvedPipelineMap =
                  AuthenticatedRequest.allowAnonymous(
                      () -> Retrofit2SyncCall.execute(orca.plan(pipelineToPlan, true)));
              pipeline = objectMapper.convertValue(resolvedPipelineMap, Pipeline.class);
            } catch (SpinnakerServerException e) {
              log.error(
                  "Failed planning {}: \n{}",
                  pipeline,
                  e.getMessage() == null ? "N/A" : e.getMessage());

              // Continue anyway, so that the execution will appear in Deck
              pipeline = pipeline.withPlan(false);
              if (pipeline.getStages() == null) {
                pipeline = pipeline.withStages(Collections.emptyList());
              }
            }
            if (propagateAuth) {
              pipeline = pipeline.withTrigger(pipeline.getTrigger().atPropagateAuth(true));
            }
          }
          triggerPipeline(pipeline, triggerSource);
          registry.counter("orca.requests").increment();
        }
      } catch (Exception e) {
        log.error("Unable to trigger pipeline {}: {}", pipeline, e);
        logOrcaErrorMetric(e.getClass().getName(), triggerSource.name(), getTriggerType(pipeline));
      }
    } else {
      log.info(
          "Would trigger {} due to {} but triggering is disabled", pipeline, pipeline.getTrigger());
      registry
          .counter(
              "orca.trigger.disabled",
              "triggerSource",
              triggerSource.name(),
              "triggerType",
              getTriggerType(pipeline))
          .increment();
    }
  }

  private void triggerPipeline(Pipeline pipeline, TriggerSource triggerSource)
      throws RejectedExecutionException {
    Callable<Void> triggerWithCapturedContext =
        AuthenticatedRequest.propagate(() -> triggerPipelineImpl(pipeline, triggerSource));

    executorService.submit(triggerWithCapturedContext);
  }

  private Void triggerPipelineImpl(Pipeline pipeline, TriggerSource triggerSource) {
    try {
      TriggerResponse response;

      if (pipeline.getTrigger() != null && pipeline.getTrigger().isPropagateAuth()) {
        response = triggerWithRetries(pipeline);
      } else {
        // default to anonymous consistent with the existing pattern of
        // `AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")`
        String runAsUser = "anonymous";
        Collection<String> allowedAccounts = Collections.emptySet();

        if (fiatStatus.isEnabled()) {
          if (pipeline.getTrigger() != null
              && StringUtils.isNotBlank(pipeline.getTrigger().getRunAsUser())) {
            runAsUser = pipeline.getTrigger().getRunAsUser().trim();
          }
          allowedAccounts = getAllowedAccountsForUser(runAsUser);
        }

        response =
            AuthenticatedRequest.runAs(
                    runAsUser, allowedAccounts, () -> triggerWithRetries(pipeline))
                .call();
      }

      log.info("Successfully triggered {}: execution id: {}", pipeline, response.getRef());

      registry
          .counter(
              "orca.trigger.success",
              "triggerSource",
              triggerSource.name(),
              "triggerType",
              getTriggerType(pipeline))
          .increment();
    } catch (SpinnakerHttpException e) {
      String orcaResponse = "N/A";
      if (e.getResponseBody() != null) {
        orcaResponse = e.getResponseBody().toString();
      }

      log.error(
          "Failed to trigger {} HTTP: {}\norca error: {}\npayload: {}",
          pipeline,
          e.getResponseCode(),
          orcaResponse,
          pipelineAsString(pipeline));

      logOrcaErrorMetric(e.getClass().getName(), triggerSource.name(), getTriggerType(pipeline));
    } catch (Exception e) {
      log.error(
          "Failed to trigger {}\nerror: {}\npayload: {}", pipeline, e, pipelineAsString(pipeline));

      logOrcaErrorMetric(e.getClass().getName(), triggerSource.name(), getTriggerType(pipeline));
    }

    return null;
  }

  private TriggerResponse triggerWithRetries(Pipeline pipeline) {
    int attempts = 0;

    while (true) {
      try {
        attempts++;
        return Retrofit2SyncCall.execute(orca.trigger(pipeline));
      } catch (SpinnakerServerException e) {
        if ((attempts >= retryCount) || (e.getRetryable() != null && !e.getRetryable())) {
          throw e;
        } else {
          log.warn(
              "Error triggering {} with {} (attempt {}/{}). Retrying...",
              pipeline,
              e,
              attempts,
              retryCount);
        }
      }

      try {
        Thread.sleep(retryDelayMillis);
        registry.counter("orca.trigger.retries").increment();
      } catch (InterruptedException ignored) {
      }
    }
  }
  /**
   * The set of accounts that a user has WRITE access to.
   *
   * <p>Similar filtering can be found in `gate` (see AllowedAccountsSupport.java).
   *
   * @param user A service account name (or 'anonymous' if not specified)
   * @return the allowed accounts for {@param user} as determined by fiat
   */
  private Set<String> getAllowedAccountsForUser(String user) {
    if (fiatPermissionEvaluator == null || !fiatStatus.isLegacyFallbackEnabled()) {
      return Collections.emptySet();
    }

    UserPermission.View userPermission = null;
    try {
      userPermission =
          AuthenticatedRequest.allowAnonymous(() -> fiatPermissionEvaluator.getPermission(user));
    } catch (Exception e) {
      log.error("Unable to fetch permission for {}", user, e);
    }

    if (userPermission == null) {
      return Collections.emptySet();
    }

    return userPermission.getAccounts().stream()
        .filter(v -> v.getAuthorizations().contains(Authorization.WRITE))
        .map(Account.View::getName)
        .collect(Collectors.toSet());
  }

  private void logOrcaErrorMetric(String exceptionName, String triggerSource, String triggerType) {
    registry
        .counter(
            "orca.errors",
            "exception",
            exceptionName,
            "triggerSource",
            triggerSource,
            "triggerType",
            triggerType)
        .increment();

    registry
        .counter(
            "orca.trigger.errors",
            "exception",
            exceptionName,
            "triggerSource",
            triggerSource,
            "triggerType",
            triggerType)
        .increment();
  }

  private String pipelineAsString(Pipeline pipeline) {
    try {
      return objectMapper.writeValueAsString(pipeline);
    } catch (JsonProcessingException jsonException) {
      log.warn("Failed to convert pipeline to json, using raw toString", jsonException);
      return pipeline.toString();
    }
  }

  private String getTriggerType(Pipeline pipeline) {
    if (pipeline.getTrigger() != null) {
      return pipeline.getTrigger().getType();
    }

    return "N/A";
  }

  /**
   * Checks if the specified trigger type is enabled
   *
   * @param triggerSource trigger type/source
   * @return true if enabled, false otherwise
   */
  private boolean isEnabled(TriggerSource triggerSource) {
    boolean triggerEnabled = true;

    if (!discoveryStatusListener.isEnabled()) {
      return false;
    }

    if (triggerSource == TriggerSource.COMPENSATION_SCHEDULER) {
      triggerEnabled = dynamicConfigService.isEnabled("scheduler.compensation-job.triggers", true);
    } else if (triggerSource == TriggerSource.CRON_SCHEDULER) {
      triggerEnabled = dynamicConfigService.isEnabled("scheduler.triggers", true);
    }

    return triggerEnabled && dynamicConfigService.isEnabled("orca", true);
  }
}
