/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;

import java.util.Arrays;
import java.util.List;

/**
 * Listener that delegates to other listeners in "onion order".
 */
public class CompositeExecutionListener implements ExecutionListener {
  private final List<ExecutionListener> delegates;
  private final List<ExecutionListener> reversed;

  public CompositeExecutionListener(ExecutionListener ... delegates) {
    this.delegates = Arrays.asList(delegates);
    this.reversed = Lists.reverse(this.delegates);
  }

  @Override
  public void beforeExecution(Persister persister, Execution execution) {
    delegates.forEach(it -> it.beforeExecution(persister, execution));
  }

  @Override
  public void afterExecution(Persister persister, Execution execution, ExecutionStatus executionStatus, boolean wasSuccessful) {
    reversed.forEach(it -> it.afterExecution(persister, execution, executionStatus, wasSuccessful));
  }
}
