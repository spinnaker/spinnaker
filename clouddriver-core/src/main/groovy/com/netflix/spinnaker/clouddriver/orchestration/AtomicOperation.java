/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration;

import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An AtomicOperation is the most fundamental, low-level unit of work in a workflow. Implementations
 * of this interface should perform the simplest form of work possible, often described by a
 * description object (like {@link com.netflix.spinnaker.clouddriver.deploy.DeployDescription}
 */
public interface AtomicOperation<R> {
  /**
   * This method will initiate the operation's work. In this, operation's can get a handle on prior
   * output results from the requiremed method argument.
   *
   * @param priorOutputs
   * @return parameterized type
   */
  R operate(List priorOutputs);

  default Collection<OperationEvent> getEvents() {
    return Collections.emptyList();
  }
}
