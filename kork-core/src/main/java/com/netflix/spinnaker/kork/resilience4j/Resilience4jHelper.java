package com.netflix.spinnaker.kork.resilience4j;

import io.github.resilience4j.core.EventConsumer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryEvent;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnIgnoredErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.retry.event.RetryOnSuccessEvent;
import org.slf4j.Logger;

public class Resilience4jHelper {
  /** Configure helpful logging for RetryRegistry objects */
  public static void configureLogging(RetryRegistry retryRegistry, String description, Logger log) {
    // log whenever a new retry instance is added, removed or replaced from the registry
    retryRegistry
        .getEventPublisher()
        .onEntryAdded(
            entryAddedEvent -> {
              Retry addedRetry = entryAddedEvent.getAddedEntry();
              log.info("{} retries configured for: {}", description, addedRetry.getName());
            })
        .onEntryRemoved(
            entryRemovedEvent -> {
              Retry removedRetry = entryRemovedEvent.getRemovedEntry();
              log.info("{} retries removed for: {}", description, removedRetry.getName());
            })
        .onEntryReplaced(
            entryReplacedEvent -> {
              Retry oldEntry = entryReplacedEvent.getOldEntry();
              Retry newEntry = entryReplacedEvent.getNewEntry();
              log.info(
                  "{} retries: {} updated to: {}",
                  description,
                  oldEntry.getName(),
                  newEntry.getName());
            });

    // Define an event consumer once for the entire registry as mentioned here:
    // https://github.com/resilience4j/resilience4j/issues/974#issuecomment-619956673
    //
    // If we don't do this once, but add it for each individual retry
    // instance, and if that retry instance is invoked by multiple threads,
    // then there is a lot of log duplication.  For example, if 10 threads get
    // an object from s3, there will be (10 * num_retries) log lines for each
    // retry event instead of just the num_retries that we expect.
    EventConsumer<RetryEvent> eventConsumer =
        retryEvent -> {
          if (retryEvent instanceof RetryOnErrorEvent) {
            log.error(
                "{} for {} failed after {} attempts. Exception: {}",
                description,
                retryEvent.getName(),
                retryEvent.getNumberOfRetryAttempts(),
                String.valueOf(retryEvent.getLastThrowable()));
          } else if (retryEvent instanceof RetryOnSuccessEvent) {
            log.info(
                "{} for {} is now successful in attempt #{}. Last attempt had failed with exception: {}",
                description,
                retryEvent.getName(),
                retryEvent.getNumberOfRetryAttempts() + 1,
                String.valueOf(retryEvent.getLastThrowable()));
          } else if (retryEvent instanceof RetryOnRetryEvent) {
            log.info(
                "Retrying {} for {}. Attempt #{} failed with exception: {}",
                description,
                retryEvent.getName(),
                retryEvent.getNumberOfRetryAttempts(),
                String.valueOf(retryEvent.getLastThrowable()));
          } else if (!(retryEvent instanceof RetryOnIgnoredErrorEvent)) {
            // don't log anything for Ignored exceptions as it just leads to noise in the logs
            log.info(retryEvent.toString());
          }
        };
    retryRegistry
        .getAllRetries()
        .forEach(retry -> retry.getEventPublisher().onEvent(eventConsumer));
    retryRegistry
        .getEventPublisher()
        .onEntryAdded(event -> event.getAddedEntry().getEventPublisher().onEvent(eventConsumer));
  }
}
