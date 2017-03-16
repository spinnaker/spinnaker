package com.netflix.spinnaker.orca.batch;

import java.util.Collection;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.StageListener;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecutionListener;

public interface ExecutionListenerProvider {
  StepExecutionListener wrap(StageListener stageListener);

  JobExecutionListener wrap(ExecutionListener executionListener);

  Collection<StepExecutionListener> allStepExecutionListeners();

  Collection<JobExecutionListener> allJobExecutionListeners();
}
