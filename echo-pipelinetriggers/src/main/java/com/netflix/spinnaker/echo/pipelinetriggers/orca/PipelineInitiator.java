package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService.TriggerResponse;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.RetrofitError.Kind;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import javax.annotation.PostConstruct;
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
  private final boolean enabled;
  private final int retryCount;
  private final long retryDelayMillis;

  @Autowired
  public PipelineInitiator(Registry registry,
                           OrcaService orca,
                           FiatStatus fiatStatus,
                           @Value("${orca.enabled:true}") boolean enabled,
                           @Value("${orca.pipelineInitiatorRetryCount:5}") int retryCount,
                           @Value("${orca.pipelineInitiatorRetryDelayMillis:5000}") long retryDelayMillis) {
    this.registry = registry;
    this.orca = orca;
    this.fiatStatus = fiatStatus;
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
      log.info("Triggering {} due to {}", pipeline, pipeline.getTrigger());
      registry.counter("orca.requests").increment();

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
    } else {
      log.info("Would trigger {} due to {} but triggering is disabled", pipeline, pipeline.getTrigger());
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
    return error instanceof RetrofitError &&
      (((RetrofitError) error).getKind() == Kind.NETWORK || ((RetrofitError) error).getKind() == Kind.HTTP);
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
          if (++retryCount < maxRetries) {
            log.error("Retrying pipeline trigger, attempt {}/{}", retryCount, maxRetries);
            return Observable.timer(retryDelayMillis, TimeUnit.MILLISECONDS);
          }
          return Observable.error(throwable);
        });
    }
  }
}
