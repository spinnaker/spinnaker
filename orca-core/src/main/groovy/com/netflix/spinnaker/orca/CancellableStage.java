package com.netflix.spinnaker.orca;

import java.util.Map;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

public interface CancellableStage {
  Result cancel(Stage stage);

  class Result {
    private final String stageId;
    private final Map details;

    public Result(Stage stage, Map details) {
      this.stageId = stage.getId();
      this.details = details;
    }

    public final String getStageId() {
      return stageId;
    }

    public final Map getDetails() {
      return details;
    }
  }
}
