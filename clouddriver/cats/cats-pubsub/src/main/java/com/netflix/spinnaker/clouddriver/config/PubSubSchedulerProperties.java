package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.kork.annotations.Alpha;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "cats.pubsub")
@Component
@Data
@Alpha
public class PubSubSchedulerProperties {
  private int delayBetweenSchedulerRunsMs =
      15000; // Run every 15 seconds to refresh state & queue as needed
  private int minutesBeforeDeletingMarkedForDeletion =
      180; // IF an agent is marked for deletion, wait 3 hours before ACTUALLY deleting it in case
  // it comes back
  private int minutesBeforeReQueueOfAgents =
      20; // If an agent hasn't moved out of PENDING in this window, its stream record was lost -
  // re-enqueue it.  Also the idle threshold for reclaiming unacknowledged records from dead
  // consumers.
  private int maxConcurrentAgents =
      100; // Max concurrent executions per replica.  Sizes the runner worker pool; runners never
  // pull more records than they have free workers.
  private long streamMaxLength =
      100_000; // Approximate cap on the redis stream length (XTRIM each scheduler cycle).
  // Acknowledged records are not removed automatically, so this bounds redis memory.
}
