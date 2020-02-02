/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.events;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.listeners.DefaultPersister;
import com.netflix.spinnaker.orca.listeners.ExecutionListener;
import com.netflix.spinnaker.orca.listeners.Persister;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.slf4j.MDC;
import org.springframework.context.ApplicationListener;

/** Adapts events emitted by the nu-orca queue to an old-style listener. */
public final class ExecutionListenerAdapter implements ApplicationListener<ExecutionEvent> {

  private final ExecutionListener delegate;
  private final ExecutionRepository repository;
  private final Persister persister;

  public ExecutionListenerAdapter(ExecutionListener delegate, ExecutionRepository repository) {
    this.delegate = delegate;
    this.repository = repository;
    persister = new DefaultPersister(repository);
  }

  @Override
  public void onApplicationEvent(ExecutionEvent event) {
    try {
      MDC.put(Header.EXECUTION_ID.getHeader(), event.getExecutionId());
      if (event instanceof ExecutionStarted) {
        onExecutionStarted((ExecutionStarted) event);
      } else if (event instanceof ExecutionComplete) {
        onExecutionComplete((ExecutionComplete) event);
      }
    } finally {
      MDC.remove(Header.EXECUTION_ID.getHeader());
    }
  }

  private void onExecutionStarted(ExecutionStarted event) {
    delegate.beforeExecution(persister, executionFor(event));
  }

  private void onExecutionComplete(ExecutionComplete event) {
    ExecutionStatus status = event.getStatus();
    delegate.afterExecution(persister, executionFor(event), status, status.isSuccessful());
  }

  private Execution executionFor(ExecutionEvent event) {
    return repository.retrieve(event.getExecutionType(), event.getExecutionId());
  }
}
