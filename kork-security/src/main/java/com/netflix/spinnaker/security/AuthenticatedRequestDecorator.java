/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.security;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.ThreadContext;

/**
 * Provides decorators for {@link Runnable} and {@link Callable} for propagating the current {@link
 * ThreadContext}/{@link org.slf4j.MDC}.
 */
@NonnullByDefault
public class AuthenticatedRequestDecorator {
  public static Runnable wrap(Runnable runnable) {
    Map<String, String> context = ThreadContext.getContext();
    // this should not be null, but sometimes it is; see:
    // https://github.com/apache/logging-log4j2/issues/1426
    if (context == null) {
      return runnable;
    }
    return () -> {
      Map<String, String> originalContext = ThreadContext.getContext();
      ThreadContext.clearMap();
      ThreadContext.putAll(context);
      try {
        runnable.run();
      } finally {
        ThreadContext.clearMap();
        if (originalContext != null) {
          ThreadContext.putAll(originalContext);
        }
      }
    };
  }

  public static <V> Callable<V> wrap(Callable<V> callable) {
    Map<String, String> context = ThreadContext.getContext();
    // this should not be null, but sometimes it is; see:
    // https://github.com/apache/logging-log4j2/issues/1426
    if (context == null) {
      return callable;
    }
    return () -> {
      Map<String, String> originalContext = ThreadContext.getContext();
      ThreadContext.clearMap();
      ThreadContext.putAll(context);
      try {
        return callable.call();
      } finally {
        ThreadContext.clearMap();
        if (originalContext != null) {
          ThreadContext.putAll(originalContext);
        }
      }
    };
  }
}
