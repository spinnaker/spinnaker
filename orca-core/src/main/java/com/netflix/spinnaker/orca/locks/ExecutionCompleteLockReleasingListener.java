package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.orca.events.ExecutionComplete;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ExecutionCompleteLockReleasingListener implements ApplicationListener<ExecutionComplete> {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ExecutionRepository executionRepository;
  private final LockManager lockManager;
  private final LockingConfigurationProperties lockingConfigurationProperties;

  @Autowired
  public ExecutionCompleteLockReleasingListener(ExecutionRepository executionRepository,
                                                LockManager lockManager,
                                                LockingConfigurationProperties lockingConfigurationProperties) {
    this.executionRepository = executionRepository;
    this.lockManager = lockManager;
    this.lockingConfigurationProperties = lockingConfigurationProperties;
  }

  @Override
  public void onApplicationEvent(ExecutionComplete event) {
    if (!lockingConfigurationProperties.isEnabled()) {
      return;
    }
    if (event.getStatus().isHalt()) {
      Execution execution = executionRepository.retrieve(event.getExecutionType(), event.getExecutionId());
      execution.getStages().forEach(s -> {
        if (AcquireLockStage.PIPELINE_TYPE.equals(s.getType())) {
          try {
            LockContext lc = s.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(s).build();
            lockManager.releaseLock(lc.getLockName(), lc.getLockValue(), lc.getLockHolder());
          } catch (LockFailureException lfe) {
            logger.info("Failure releasing lock in ExecutionCompleteLockReleasingListener - ignoring", lfe);
          }
        }
      });
    }
  }
}
