/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.services;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

/** Make a copy of the MDC at construction time available to a task. */
public abstract class MdcWrappedCallable<T> implements Callable<T> {
  private final Map<String, String> contextMap;

  public MdcWrappedCallable() {
    this.contextMap = MDC.getCopyOfContextMap();
  }

  /** Compute a result, with the MDC as it was at construction time of this object */
  public abstract T callWithMdc() throws Exception;

  @Override
  public T call() throws Exception {
    if (contextMap == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(contextMap);
    }
    return callWithMdc();
  }
}
