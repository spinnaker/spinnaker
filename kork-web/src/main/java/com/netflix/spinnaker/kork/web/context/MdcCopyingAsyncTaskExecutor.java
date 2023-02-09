/*
 * Copyright 2022 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.web.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.slf4j.MDC;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Copy the MDC before executing tasks. This supports async controller methods (e.g. that return
 * StreamingResponseBody) passing X-SPINNAKER-* headers to downstream requests, and including the
 * MDC from the calling thread in log messages.
 *
 * <p>The typical pattern is:
 *
 * <p>AuthenticatedRequestFilter: copies X-SPINNAKER-* incoming request headers into the MDC
 * SpinnakerRequestInterceptor: copies X-SPINNAKER-* from the MDC into outgoing request headers
 *
 * <p>MdcCopyingAsyncTaskExecutor makes it so SpinnakerRequestInterceptor has something to copy for
 * async methods. It also makes it so log messages include X-SPINNAKER-*, specifically
 * X-SPINNAKER-REQUEST-ID and X-SPINNAKER-REQUEST-ID to faciliate troubleshooting.
 */
public class MdcCopyingAsyncTaskExecutor implements AsyncTaskExecutor {
  private final AsyncTaskExecutor delegate;

  public MdcCopyingAsyncTaskExecutor(AsyncTaskExecutor asyncTaskExecutor) {
    this.delegate = Objects.requireNonNull(asyncTaskExecutor);
  }

  private Runnable wrapWithContext(final Runnable task) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return () -> {
      if (contextMap == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(contextMap);
      }
      task.run();
    };
  }

  private <T> Callable<T> wrapWithContext(final Callable<T> callable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    if (contextMap == null) {
      return callable;
    }
    return () -> {
      MDC.setContextMap(contextMap);
      return callable.call();
    };
  }

  @Override
  public void execute(Runnable task) {
    delegate.execute(wrapWithContext(task));
  }

  @Override
  public void execute(Runnable task, long startTimeout) {
    delegate.execute(wrapWithContext(task), startTimeout);
  }

  @Override
  public java.util.concurrent.Future<?> submit(Runnable task) {
    return delegate.submit(wrapWithContext(task));
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(wrapWithContext(task));
  }
}
