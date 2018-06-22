package com.netflix.spinnaker.echo.pipelinetriggers.orca;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService.TriggerResponse;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
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
public class PipelineInitiator implements Action1<Pipeline> {

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

  @Override
  public void call(Pipeline pipeline) {
    if (enabled) {
      log.info("Triggering {} due to {}", pipeline, pipeline.getTrigger());
      registry.counter("orca.requests").increment();

      createTriggerObservable(pipeline)
        .retryWhen(new RetryWithDelay(retryCount, retryDelayMillis))
        .subscribe(this::onOrcaResponse, throwable -> onOrcaError(pipeline, throwable));
    } else {
      log.info("Would trigger {} due to {} but triggering is disabled", pipeline, pipeline.getTrigger());
    }
  }

  private Observable<OrcaService.TriggerResponse> createTriggerObservable(Pipeline pipeline) {
    String runAsUser = null;
    if (pipeline.getTrigger() != null) {
      runAsUser = pipeline.getTrigger().getRunAsUser();
    }

    return (fiatStatus.isEnabled() && runAsUser != null && !runAsUser.isEmpty()) ?
      orca.trigger(pipeline, runAsUser) :
      orca.trigger(pipeline);
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
