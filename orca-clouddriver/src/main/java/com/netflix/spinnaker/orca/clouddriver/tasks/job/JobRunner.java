package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Deployments of server groups vary wildly across cloud providers. A JobRunner is a cloud-provider
 * specific way to hook into the Orca infrastructure.
 */
public interface JobRunner {
  /**
   * @return a list of operation descriptors. Each operation should be a single entry map keyed by
   *     the operation name, with the operation map as the value.
   */
  List<Map> getOperations(StageExecution stage);

  /** @return any additional values that should be included in task outputs */
  Map<String, Object> getAdditionalOutputs(StageExecution stage, List<Map> operations);

  /** @return true if the resulting value from the Kato call should be used. */
  boolean isKatoResultExpected();

  /** @return The cloud provider type that this object supports. */
  String getCloudProvider();

  /**
   * @return the timeout in millis, if any, of the job so we can set the timeout of the task to the
   *     same
   */
  @Nonnull
  default Optional<Duration> getJobTimeout(StageExecution stage) {
    return Optional.empty();
  }

  String OPERATION = "runJob";
}
