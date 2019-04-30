package com.netflix.spinnaker.orca.gremlin.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.gremlin.AttackStatus;
import com.netflix.spinnaker.orca.gremlin.GremlinService;
import com.netflix.spinnaker.orca.gremlin.pipeline.GremlinStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.orca.gremlin.pipeline.GremlinStage.TERMINAL_KEY;

@Component
public class MonitorGremlinAttackTask implements OverridableTimeoutRetryableTask, Task {
  @Autowired
  private GremlinService gremlinService;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    final Map<String, Object> ctx = stage.getContext();

    final String apiKey = GremlinStage.getApiKey(ctx);
    final String attackGuid = GremlinStage.getAttackGuid(ctx);

    final List<AttackStatus> statuses = gremlinService.getStatus(apiKey, attackGuid);

    boolean foundFailedAttack = false;
    String failureType = "";
    String failureOutput = "";

    for (final AttackStatus status : statuses) {
      if (status.getEndTime() == null) {
        return TaskResult.builder(ExecutionStatus.RUNNING).context(ctx).build();
      }
      if (isFailure(status.getStageLifecycle())) {
        foundFailedAttack = true;
        failureType = status.getStage();
        failureOutput = status.getOutput();
      }
    }
    ctx.put(TERMINAL_KEY, "true");
    if (foundFailedAttack) {
      throw new RuntimeException("Gremlin run failed (" + failureType + ") with output : " + failureOutput);
    } else {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(ctx).build();
    }
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(10);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(15);
  }

  private boolean isFailure(final String gremlinStageName) {
    return gremlinStageName.equals("Error");
  }
}
