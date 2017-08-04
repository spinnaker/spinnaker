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

package com.netflix.spinnaker.orca;

public class ExecutionContext {
  private static final ThreadLocal<ExecutionContext> threadLocal = new ThreadLocal<>();

  private final String application;
  private final String executionType;
  private final String executionId;

  public ExecutionContext(String application, String executionType, String executionId) {
    this.application = application;
    this.executionType = executionType;
    this.executionId = executionId;
  }

  public static void set(ExecutionContext executionContext) {
    threadLocal.set(executionContext);
  }

  public static ExecutionContext get() {
    return threadLocal.get();
  }

  public static void clear() {
    threadLocal.remove();
  }

  public String getApplication() {
    return application;
  }

  public String getExecutionType() {
    return executionType;
  }

  public String getExecutionId() {
    return executionId;
  }
}
