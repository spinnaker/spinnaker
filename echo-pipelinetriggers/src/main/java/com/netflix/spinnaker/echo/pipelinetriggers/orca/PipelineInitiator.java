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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pipelinetriggers.QuietPeriodIndicator;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService.TriggerResponse;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import javax.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.RetrofitError.Kind;
import retrofit.client.Response;
import rx.Observable;
import rx.functions.Func1;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Triggers a {@link Pipeline} by invoking _Orca_.
 */
@Component
@Slf4j
public class PipelineInitiator {

  private final Registry registry;
  private final OrcaService orca;
  private final FiatStatus fiatStatus;
  private final ObjectMapper objectMapper;
  private final QuietPeriodIndicator quietPeriodIndicator;
  private final boolean enabled;
  private final int retryCount;
  private final long retryDelayMillis;

  @Autowired
  public PipelineInitiator(@NonNull Registry registry,
                           @NonNull OrcaService orca,
                           @NonNull FiatStatus fiatStatus,
                           ObjectMapper objectMapper,
                           @NonNull QuietPeriodIndicator quietPeriodIndicator,
                           @Value("${orca.enabled:true}") boolean enabled,
                           @Value("${orca.pipelineInitiatorRetryCount:5}") int retryCount,
                           @Value("${orca.pipelineInitiatorRetryDelayMillis:5000}") long retryDelayMillis) {
    this.registry = registry;
    this.orca = orca;
    this.fiatStatus = fiatStatus;
    this.objectMapper = objectMapper;
    this.quietPeriodIndicator = quietPeriodIndicator;
    this.enabled = enabled;
    this.retryCount = retryCount;
    this.retryDelayMillis = retryDelayMillis;
  }

  @PostConstruct
  public void initialize() {
    if (!enabled) {
      log.warn("Orca triggering is disabled");
    }
  }

  public void startPipeline(Pipeline pipeline) {
    if (enabled) {
      if (pipeline.getTrigger() != null &&
          pipeline.isRespectQuietPeriod() &&
          quietPeriodIndicator.inQuietPeriod(System.currentTimeMillis(), pipeline.getTrigger().getType())) {
        log.info("Would trigger {} due to {} but pipeline is set to ignore automatic triggers during quiet periods", pipeline, pipeline.getTrigger());
      } else {
        log.info("Triggering {} due to {}", pipeline, pipeline.getTrigger());

        final String templatedPipelineType = "templatedPipeline";
        if (templatedPipelineType.equals(pipeline.getType())) { // TODO(jacobkiefer): Constantize.
          // We need to store and re-set the propagateAuth flag, as it is ignored on deserialization
          // TODO(ezimanyi): Find a better way to pass the propagateAuth flag than on the trigger itself
          boolean propagateAuth = pipeline.getTrigger() != null && pipeline.getTrigger().isPropagateAuth();
          log.debug("Planning templated pipeline {} before triggering", pipeline.getId());
          pipeline = pipeline.withPlan(true);
          Map resolvedPipelineMap = orca.plan(objectMapper.convertValue(pipeline, Map.class));
          pipeline = objectMapper.convertValue(resolvedPipelineMap, Pipeline.class);
          if (propagateAuth) {
            pipeline = pipeline.withTrigger(pipeline.getTrigger().atPropagateAuth(true));
          }
        }
        triggerPipeline(pipeline);
        registry.counter("orca.requests").increment();
      }
    } else {
      log.info("Would trigger {} due to {} but triggering is disabled", pipeline, pipeline.getTrigger());
    }
  }

  private void triggerPipeline(Pipeline pipeline) {
    Observable<OrcaService.TriggerResponse> orcaResponse = createTriggerObservable(pipeline)
      .retryWhen(new RetryWithDelay(retryCount, retryDelayMillis))
      .doOnNext(this::onOrcaResponse)
      .doOnError(throwable -> onOrcaError(pipeline, throwable));

    if (pipeline.getTrigger() != null && pipeline.getTrigger().isPropagateAuth()) {
      // If the trigger is one that should propagate authentication, just directly call Orca as the request interceptor
      // will pass along the current headers.
      orcaResponse.subscribe();
    } else {
      // If we should not propagate authentication, create an empty User object for the request
      User korkUser = new User();
      if (fiatStatus.isEnabled() && pipeline.getTrigger() != null) {
        korkUser.setEmail(pipeline.getTrigger().getRunAsUser());
      }
      try {
        AuthenticatedRequest.propagate(() -> orcaResponse.subscribe(), korkUser).call();
      } catch (Exception e) {
        log.error("Unable to trigger pipeline {}: {}", pipeline, e);
      }
    }
  }

  private Observable<OrcaService.TriggerResponse> createTriggerObservable(Pipeline pipeline) {
    return orca.trigger(pipeline);
  }

  private void onOrcaResponse(TriggerResponse response) {
    log.info("Triggered pipeline {}", response.getRef());
  }

  private void onOrcaError(Pipeline pipeline, Throwable error) {
    registry.counter("orca.errors", "exception", error.getClass().getName()).increment();
    log.error("Error triggering pipeline: {}", pipeline, error);
  }

  private static boolean isRetryable(Throwable error) {
    if (!(error instanceof RetrofitError)) {
      return false;
    }
    RetrofitError retrofitError = (RetrofitError) error;

    if (retrofitError.getKind() == Kind.NETWORK) {
      return true;
    }

    if (retrofitError.getKind() == Kind.HTTP) {
      Response response = retrofitError.getResponse();
      return (response != null && response.getStatus() != HttpStatus.BAD_REQUEST.value());
    }

    return false;
  }

  private static class RetryWithDelay implements Func1<Observable<? extends Throwable>, Observable<?>> {

    private final int maxRetries;
    private final long retryDelayMillis;
    private int retryCount;

    RetryWithDelay(int maxRetries, long retryDelayMillis) {
      this.maxRetries = maxRetries;
      this.retryDelayMillis = retryDelayMillis;
      this.retryCount = 0;
    }

    @Override
    public Observable<?> call(Observable<? extends Throwable> attempts) {
      return attempts
        .flatMap((Func1<Throwable, Observable<?>>) throwable -> {
          if (isRetryable(throwable) && ++retryCount < maxRetries) {
            log.error("Retrying pipeline trigger, attempt {}/{}", retryCount, maxRetries);
            return Observable.timer(retryDelayMillis, TimeUnit.MILLISECONDS);
          }
          return Observable.error(throwable);
        });
    }
  }
}
