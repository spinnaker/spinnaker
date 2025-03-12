package com.netflix.spinnaker.keel.actuation;

import com.netflix.spinnaker.keel.api.Resource;
import com.netflix.spinnaker.keel.api.ResourceKind;
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler;
import com.netflix.spinnaker.keel.api.plugins.SupportedKind;
import com.netflix.spinnaker.keel.api.support.EventPublisher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Java resource handler whose only feature is to be able to block before returning results
 * to [currentAsync] and [desiredAsync] by sleeping a configurable amount of milliseconds.
 *
 * <p>Pass a `delay` entry in the `data` map of the [MapBackedResourceSpec] to configure the sleep
 * time.
 */
public class SleepyJavaResourceHandler
    implements ResourceHandler<MapBackedResourceSpec, MapBackedResourceSpec> {
  public static final ResourceKind SLEEPY_RESOURCE_KIND = new ResourceKind("test", "sleepy", "1");
  public static final Logger LOG = LoggerFactory.getLogger(SleepyJavaResourceHandler.class);

  private EventPublisher eventPublisher;

  public SleepyJavaResourceHandler(EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Nonnull
  @Override
  public SupportedKind<MapBackedResourceSpec> getSupportedKind() {
    return new SupportedKind<>(SLEEPY_RESOURCE_KIND, MapBackedResourceSpec.class);
  }

  @Nullable
  @Override
  public EventPublisher getEventPublisher() {
    return eventPublisher;
  }

  @Nonnull
  @Override
  public CompletableFuture<MapBackedResourceSpec> desiredAsync(
      @Nonnull Resource<? extends MapBackedResourceSpec> resource, @Nonnull Executor executor) {
    return CompletableFuture.supplyAsync(
        () -> {
          sleep(resource);
          return resource.getSpec();
        },
        executor);
  }

  @Nonnull
  @Override
  public CompletableFuture<MapBackedResourceSpec> currentAsync(
      @Nonnull Resource<? extends MapBackedResourceSpec> resource, @Nonnull Executor executor) {
    return CompletableFuture.supplyAsync(
        () -> {
          sleep(resource);
          return resource.getSpec();
        },
        executor);
  }

  private void sleep(Resource<? extends MapBackedResourceSpec> resource) {
    String delay = (String) resource.getSpec().getData().get("delay");
    if (delay != null) {
      try {
        LOG.debug("Sleeping for {} millis...", delay);
        Thread.sleep(Long.parseLong(delay));
        LOG.debug("Done sleeping.");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
