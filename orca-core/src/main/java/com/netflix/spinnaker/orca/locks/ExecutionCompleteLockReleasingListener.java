/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.orca.events.ExecutionComplete;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent.AGENT_MDC_KEY;

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
      try {
        MDC.put(AGENT_MDC_KEY, this.getClass().getSimpleName());

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
      } finally {
        MDC.remove(AGENT_MDC_KEY);
      }
    }
  }
}
