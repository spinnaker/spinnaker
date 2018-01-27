package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.TaskResult.SUCCEEDED;

@Component
public class NoopPreconditionTask implements PreconditionTask {

  @Override public TaskResult execute(Stage stage) {
    return SUCCEEDED;
  }

  public final String getPreconditionType() {
    return "noop";
  }
}
