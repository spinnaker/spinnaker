package com.netflix.spinnaker.clouddriver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "cats.pubsub")
@Component
@Data
public class PubSubSchedulerProperties {
  private int delayBetweenSchedulerRunsMs =
      15000; // Rune very 15 seconds to refresh state & queue as needed
  private int minutesBeforeDeletingMarkedForDeletion =
      180; // IF an agent is marked for deletion, wait 3 hours before ACTUALLY deleting it in case
  // it comes back
  private int minutesBeforeReQueueOfAgents =
      20; // if an agent hasn't moved to starting in 20 minutes... requeue it
  private double percentMaxOverNormalDuration =
      1.5; // This in combination of the max duration allows an agent to run for a long time before
  // re-running it
  private int maxDurationForAnAgentMinutes =
      120; // Allow for up to two hours for an agent to complete
  private int maxConcurrentAgents =
      100; // Max concurrent runners.  Controls the listener thread pool.  "Core" threads == 1/3 of
  // this number
}
