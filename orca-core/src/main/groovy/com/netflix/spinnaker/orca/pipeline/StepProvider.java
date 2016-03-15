package com.netflix.spinnaker.orca.pipeline;

import java.util.List;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.batch.core.Step;

public interface StepProvider {
  String getType();

  <T extends Execution<T>> List<Step> buildSteps(Stage<T> stage);
}
