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
  private final String authenticatedUser;
  private final String executionType;
  private final String executionId;
  private final String stageId;
  private final String origin;

  public ExecutionContext(String application,
                          String authenticatedUser,
                          String executionType,
                          String executionId,
                          String stageId,
                          String origin) {
    this.application = application;
    this.authenticatedUser = authenticatedUser;
    this.executionType = executionType;
    this.executionId = executionId;
    this.stageId = stageId;
    this.origin = origin;
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

  public String getAuthenticatedUser() {
    return authenticatedUser;
  }

  public String getExecutionType() {
    return executionType;
  }

  public String getExecutionId() {
    return executionId;
  }

  public String getOrigin() { return origin; }

  public String getStageId() {
    return stageId;
  }
}
