package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.time.Duration;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A task that waits based on a feature toggle specified in the `pauseToggleKey` property of the
 * stage context. Useful for pausing stages as part of migrations, for instance.
 */
@Component
public class ToggleablePauseTask implements RetryableTask {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private DynamicConfigService dynamicConfigService;

  @Autowired
  public ToggleablePauseTask(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  @Nullable
  private String getPauseToggleKey(@Nonnull final StageExecution stage) {
    return (String) stage.getContext().get("pauseToggleKey");
  }

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull final StageExecution stage) {
    final String pauseToggleKey = getPauseToggleKey(stage);

    if (pauseToggleKey == null) {
      log.error(
          "ToggleablePauseTask added to stage without pauseToggleKey in stage context. This is a bug.");
      // we return SUCCEEDED here because this is not the user's fault...
      return TaskResult.SUCCEEDED;
    }

    if (dynamicConfigService.isEnabled(pauseToggleKey, false)) {
      log.debug(
          "{} stage currently paused based on {} toggle. Waiting...",
          stage.getName(),
          pauseToggleKey);
      return TaskResult.RUNNING;
    } else {
      log.debug(
          "{} stage currently unpaused based on {} toggle. Carrying on...",
          stage.getName(),
          pauseToggleKey);
      return TaskResult.SUCCEEDED;
    }
  }

  @Override
  public long getBackoffPeriod() {
    return Duration.ofMinutes(1).toMillis();
  }

  @Override
  public long getTimeout() {
    return Duration.ofHours(24).toMillis();
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    log.warn("ToggleablePauseTask with toggle key {} timed out.", getPauseToggleKey(stage));
    return null;
  }
}
