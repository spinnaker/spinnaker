/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security;

public class RequestContext {
  private static final ThreadLocal<RequestContext> threadLocal = new ThreadLocal<>();

  private String application;
  private String authenticatedUser;
  private String executionType;
  private String executionId;
  private String origin;

  public RequestContext(String origin, String authenticatedUser) {
    this.origin = origin;
     this.authenticatedUser = authenticatedUser != null ? authenticatedUser : "anonymous";
  }

  public static void set(RequestContext requestContext) {
    threadLocal.set(requestContext);
  }

  public static void setApplication(String application) {
    threadLocal.get().application = application;
  }

  public static void setExecutionType(String executionType) {
    threadLocal.get().executionType = executionType;
  }

  public static void setExecutionId(String executionId) {
    threadLocal.get().executionId = executionId;
  }

  public static RequestContext get() { return threadLocal.get(); }

  public static void clear() { threadLocal.remove(); }

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
}
